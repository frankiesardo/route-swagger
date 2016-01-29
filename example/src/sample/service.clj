(ns sample.service
  (:require [route-swagger.interceptor :as sw.int]
            [route-swagger.doc :as sw.doc]
            [ring.swagger.middleware :refer [stringify-error]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :as definition]
            [io.pedestal.impl.interceptor :refer [terminate]]
            [io.pedestal.interceptor :as i]
            [io.pedestal.interceptor.error :as error]
            [io.pedestal.http.body-params :refer [body-params]]
            [ring.util.http-response :as resp]
            [ring.util.http-status :as status]
            [ring.util.codec :as codec]
            [schema.core :as s]
            [clojure.set :as set]))

;;;; Schemas

(def opt s/optional-key)
(def req s/required-key)

(s/defschema Category
  {(req :id)   s/Int
   (req :name) s/Str})

(s/defschema Tag
  {(req :id)   s/Int
   (req :name) s/Str})

(s/defschema Pet
  {(req :id)       s/Int
   (req :name)     s/Str
   (opt :category) Category
   (opt :tags)     [Tag]
   (opt :status)   (s/enum "available" "pending" "sold")})

(s/defschema PetList
  {(req :total) s/Int
   (req :pets)  [Pet]})

(def PartialPet
  {(opt :name)   s/Str
   (opt :status) (s/enum "available" "pending" "sold")})

;

(s/defschema User
  {(req :username)   s/Str
   (req :password)   s/Str
   (opt :first-name) s/Str
   (opt :last-name)  s/Str
   (opt :status)     (s/enum "registered" "active" "closed")})

;

(s/defschema NewOrder
  {(req :pet-id)  s/Int
   (req :user-id) s/Int
   (opt :notes)   s/Str})

(s/defschema Order
  (assoc NewOrder
    (req :id) s/Int
    (req :status) s/Str
    (req :ship-date) s/Inst))

;; Store

(defn- created [location]
  {:status 201 :headers {"Location" location}})

(def pet-store (atom {}))

(def get-all-pets
  (sw.doc/annotate
    {:summary   "Get all pets in the store"
     :responses {status/ok {:body PetList}}}
    (i/interceptor
      {:name  ::get-all-pets
       :enter (fn [ctx]
                (assoc ctx :response (resp/ok (let [pets (vals (:pets @pet-store))]
                                                {:total (count pets)
                                                 :pets  (or pets [])}))))})))

(def add-pet
  (sw.doc/annotate
    {:summary    "Add a new pet to the store"
     :parameters {:body-params Pet}
     :responses  {status/created
                  {:headers {(req "Location") s/Str}}}}
    (i/interceptor
      {:name  ::add-pet
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [body-params]} request]
                  (swap! pet-store assoc-in [:pets (:id body-params)] body-params)
                  (assoc ctx :response (created (route/url-for ::get-pet-by-id :params {:id (:id body-params)})))))})))

(def load-pet-from-db
  (sw.doc/annotate
    {:description "Assumes a pet exists with given ID"
     :parameters  {:path-params {:id s/Int}}
     :responses   {status/not-found {}}}
    (i/interceptor
      {:name  ::load-pet-from-db
       :enter (fn [{:keys [request] :as context}]
                (if-let [pet (get-in @pet-store [:pets (-> request :path-params :id)])]
                  (assoc-in context [:request ::pet] pet)
                  (-> context
                      terminate
                      (assoc-in [:response] (resp/not-found "Pet not found")))))})))

(def get-pet-by-id
  (sw.doc/annotate
    {:summary     "Find pet by ID"
     :description "Returns a pet based on ID"
     :responses   {status/ok {:body Pet}}}
    (i/interceptor
      {:name  ::get-pet-by-id
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [::pet]} request]
                  (assoc ctx :response (resp/ok pet))))})))

(def update-pet
  (sw.doc/annotate
    {:summary    "Update an existing pet"
     :parameters {:body-params Pet}}
    (i/interceptor
      {:name  ::update-pet
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [path-params body-params]} request]
                  (swap! pet-store assoc-in [:pets (:id path-params)] body-params)
                  (assoc ctx :response (resp/ok "OK"))))})))

(def update-pet-with-form
  (sw.doc/annotate
    {:summary    "Updates a pet in the store with form data"
     :parameters {:form-params PartialPet}}
    (i/interceptor
      {:name  ::update-pet-with-form
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [path-params form-params]} request]
                  (swap! pet-store update-in [:pets (:id path-params)] merge form-params)
                  (assoc ctx :response (resp/ok "OK"))))})))

;

(def add-user
  (sw.doc/annotate
    {:summary    "Create user"
     :parameters {:body-params User}
     :responses  {status/created
                  {:headers {(req "Location") s/Str}}}}
    (i/interceptor
      {:name  ::add-user
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [body-params]} request]
                  (swap! pet-store assoc-in [:users (:username body-params)] body-params)
                  (assoc ctx :response (created (route/url-for ::get-user-by-name :params {:username (:username body-params)})))))})))

(def get-user-by-name
  (sw.doc/annotate
    {:summary    "Get user by name"
     :parameters {:path-params {:username s/Str}}
     :responses  {status/ok        {:schema User}
                  status/not-found {}}}
    (i/interceptor
      {:name  ::get-user-by-name
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [path-params]} request]
                  (assoc ctx :response (if-let [user (get-in @pet-store [:users (:username path-params)])]
                                         (resp/ok user)
                                         (resp/not-found "User not found")))))})))

;

(def add-order
  (sw.doc/annotate
    {:summary    "Create order"
     :parameters {:body-params NewOrder}
     :responses  {status/created
                  {:headers {(req "Location") s/Str}}}}
    (i/interceptor
      {:name  ::add-order
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [body-params]} request]
                  (let [id (rand-int 1000000)
                        store (swap! pet-store assoc-in [:orders id] (assoc body-params :id id))]
                    (assoc ctx :response (created (route/url-for ::get-order-by-id :params {:id id}))))))})))

(def load-order-from-db
  (sw.doc/annotate
    {:description "Assumes an order exists with given ID"
     :parameters  {:path-params {:id s/Int}}
     :responses   {status/not-found {}}}
    (i/interceptor
      {:name  ::load-order-from-db
       :enter (fn [{:keys [request] :as context}]
                (if-let [order (get-in @pet-store [:orders (-> request :path-params :id)])]
                  (assoc-in context [:request ::order] order)
                  (-> context
                      terminate
                      (assoc-in [:response] (resp/not-found "Order not found")))))})))


(def get-order-by-id
  (sw.doc/annotate
    {:summary   "Get user by name"
     :responses {status/ok {:body Order}}}
    (i/interceptor
      {:name  ::get-order-by-id
       :enter (fn [{:keys [request] :as ctx}]
                (let [{:keys [::order]} request]
                  (assoc ctx :response (resp/ok (assoc order :status "Pending" :ship-date (java.util.Date.))))))})))
;


(def basic-auth
  (sw.doc/annotate
    {:description "Check basic auth credentials"
     ;:security {"basic" []}
     :responses   {status/unauthorized {}}}
    (i/interceptor
      {:name  ::basic-auth
       :enter (fn [{:keys [request] :as context}]
                (let [auth (get-in request [:headers "authorization"])]
                  (if-not (= auth (str "Basic " (codec/base64-encode (.getBytes "foo:bar"))))
                    (-> context
                        terminate
                        (assoc-in [:response] (resp/unauthorized)))
                    context)))})))

(def delete-db
  (sw.doc/annotate
    {:summary "Delete db"}
    (i/interceptor
      {:name  ::delete-db
       :enter (fn [ctx]
                (reset! pet-store {})
                (assoc ctx :response (resp/no-content)))})))

;;;; Routes

(defn doc
  "Adds metatata m to a swagger route"
  [m]
  (sw.doc/annotate
    m
    (i/interceptor
      {:name  ::doc
       :enter identity})))

(def common-body
  (i/interceptor
    {:name  ::common-body
     :enter (fn [context]
              (update context :request set/rename-keys {:edn-params       :body-params
                                                        :json-params      :body-params
                                                        :transit-params   :body-params
                                                        :multipart-params :form-params}))}))

(def error-responses
  (sw.doc/annotate
    {:responses {400 {}
                 500 {}}}
    (error/error-dispatch [ctx ex]
      [{:interceptor ::sw.int/coerce-request}]
      (assoc ctx :response {:status 400 :body (stringify-error (:error (ex-data ex)))})

      [{:interceptor ::sw.int/validate-response}]
      (assoc ctx :response {:status 500 :body (stringify-error (:error (ex-data ex)))}))))

(defmacro defroutes [n doc routes]
  #_`(def ~n (s/with-fn-validation (-> ~routes definition/expand-routes (sw.doc/with-swagger ~doc))))
  `(def ~n (-> ~routes definition/expand-routes (sw.doc/with-swagger ~doc))))

(def swagger-json (i/interceptor (sw.int/swagger-json)))
(def swagger-ui (i/interceptor {:name  :foo
                                :enter (fn [context]
                                         (println 'HEYYY)
                                         (let [ctx ((:enter (sw.int/swagger-ui)) context)]
                                           (println ctx)
                                           ctx))}))

(defroutes routes
  {:info {:title       "Swagger Sample App"
          :description "This is a sample Petstore server."
          :version     "2.0"}
   :tags [{:name         "pets"
           :description  "Everything about your Pets"
           :externalDocs {:description "Find out more"
                          :url         "http://swagger.io"}}
          {:name        "orders"
           :description "Operations about orders"}]}
  [[["/" ^:interceptors [bootstrap/json-body
                         error-responses
                         (body-params)
                         common-body
                         (sw.int/coerce-request)
                         (sw.int/validate-response)
                         ]
     ["/pets" ^:interceptors [(doc {:tags ["pets"]})]
      {:get  get-all-pets
       :post add-pet}
      ["/:id" ^:interceptors [load-pet-from-db]
       {:get   get-pet-by-id
        :put   update-pet
        :patch update-pet-with-form}]]
     ["/users"
      {:post add-user}
      ["/:username"
       {:get get-user-by-name}]]
     ["/orders" ^:interceptors [(doc {:tags ["orders"]})]
      {:post add-order}
      ["/:id" ^:interceptors [load-order-from-db]
       {:get get-order-by-id}]]

     ["/secure" ^:interceptors [basic-auth] {:delete delete-db}]

     ["/swagger.json" {:get swagger-json}]
     ["/*resource" {:get swagger-ui}]
     ]]])

(def service {:env                      :prod
              ::bootstrap/routes        routes
              ::bootstrap/router        :linear-search
              ::bootstrap/resource-path "/public"
              ::bootstrap/type          :jetty
              ::bootstrap/port          (Integer. (or (System/getenv "PORT") 8080))})

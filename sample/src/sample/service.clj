(ns sample.service
  (:require [pedestal.swagger.core :as sw]
            [pedestal.swagger.doc :as sw.doc]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.impl.interceptor :refer [terminate]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [ring.util.http-response :as resp]
            [ring.util.http-status :as status]
            [ring.util.codec :as codec]
            [schema.core :as s]))

;;;; Schemas

(def opt s/optional-key)
(def req s/required-key)

(s/defschema Category
  {(req :id) s/Int
   (req :name) s/Str})

(s/defschema Tag
  {(req :id) s/Int
   (req :name) s/Str})

(s/defschema Pet
  {(req :id) s/Int
   (req :name) s/Str
   (opt :category) Category
   (opt :tags) [Tag]
   (opt :status) (s/enum "available" "pending" "sold")})

(s/defschema PetList
  {(req :total) s/Int
   (req :pets) [Pet]})

(def PartialPet
  {(opt :name) s/Str
   (opt :status) (s/enum "available" "pending" "sold")})

;

(s/defschema User
  {(req :username) s/Str
   (req :password) s/Str
   (opt :first-name) s/Str
   (opt :last-name) s/Str
   (opt :status) (s/enum "registered" "active" "closed")})

;

(s/defschema NewOrder
  {(req :pet-id) s/Int
   (req :user-id) s/Int
   (opt :notes) s/Str})

(s/defschema Order
  (assoc NewOrder
    (req :id) s/Int
    (req :status) s/Str
    (req :ship-date) s/Inst))

;

;; Store

(def pet-store (atom {}))

(sw/defhandler get-all-pets
  {:summary "Get all pets in the store"
   :responses {status/ok {:schema PetList}}}
  [_]
  (resp/ok (let [pets (vals (:pets @pet-store))]
              {:total (count pets)
               :pets (or pets [])})))

(sw/defhandler add-pet
  {:summary "Add a new pet to the store"
   :parameters {:body Pet}
   :responses {status/created
               {:headers {(req "Location") s/Str}}}}
  [{:keys [body-params] :as req}]
  (swap! pet-store assoc-in [:pets (:id body-params)] body-params)
  (resp/created (route/url-for ::get-pet-by-id :params {:id (:id body-params)})))

(sw/defbefore load-pet-from-db
  {:description "Assumes a pet exists with given ID"
   :parameters {:path {:id s/Int}}
   :responses {status/not-found {}}}
  [{:keys [request response] :as context}]
  (if-let [pet (get-in @pet-store [:pets (-> request :path-params :id)])]
    (assoc-in context [:request ::pet] pet)
    (-> context
        terminate
        (assoc-in [:response] (resp/not-found "Pet not found")))))

(sw/defhandler get-pet-by-id
  {:summary "Find pet by ID"
   :description "Returns a pet based on ID"
   :responses {status/ok {:schema Pet}}}
  [{:keys [::pet] :as req}]
  (resp/ok pet))

(sw/defhandler update-pet
  {:summary "Update an existing pet"
   :parameters {:body Pet}}
  [{:keys [path-params body-params] :as req}]
  (swap! pet-store assoc-in [:pets (:id path-params)] body-params)
  (resp/ok "OK"))

(sw/defhandler update-pet-with-form
  {:summary "Updates a pet in the store with form data"
   :parameters {:formData PartialPet}}
  [{:keys [path-params form-params] :as req}]
  (swap! pet-store update-in [:pets (:id path-params)] merge form-params)
  (resp/ok "OK"))

;

(sw/defhandler add-user
  {:summary "Create user"
   :parameters {:body User}
   :responses {status/created
               {:headers {(req "Location") s/Str}}}}
  [{:keys [body-params] :as req}]
  (swap! pet-store assoc-in [:users (:username body-params)] body-params)
  (resp/created (route/url-for ::get-user-by-name :params {:username (:username body-params)})))

(sw/defhandler get-user-by-name
  {:summary "Get user by name"
   :parameters {:path {:username s/Str}}
   :responses {status/ok {:schema User}
               status/not-found {}}}
  [{:keys [path-params] :as req}]
  (if-let [user (get-in @pet-store [:users (:username path-params)])]
    (resp/ok user)
    (resp/not-found "User not found")))

;

(sw/defhandler add-order
  {:summary "Create order"
   :parameters {:body NewOrder}
   :responses {status/created
               {:headers {(req "Location") s/Str}}}}
  [{:keys [body-params] :as req}]
  (let [id (rand-int 1000000)
        store (swap! pet-store assoc-in [:orders id] (assoc body-params :id id))]
    (resp/created (route/url-for ::get-order-by-id :params {:id id}))))

(sw/defbefore load-order-from-db
  {:description "Assumes an order exists with given ID"
   :parameters {:path {:id s/Int}}
   :responses {resp/not-found {}}}
  [{:keys [request response] :as context}]
  (if-let [order (get-in @pet-store [:orders (-> request :path-params :id)])]
    (assoc-in context [:request ::order] order)
    (-> context
        terminate
        (assoc-in [:response] (resp/not-found "Order not found")))))

(sw/defhandler get-order-by-id
  {:summary "Get user by name"
   :responses {status/ok {:schema Order}}}
  [{:keys [::order] :as req}]
  (resp/ok (assoc order :status "Pending" :ship-date (java.util.Date.))))

;

(sw/defbefore basic-auth
  {:description "Check basic auth credentials"
   :security {"basic" []}
   :responses {status/unauthorized {}}}
  [{:keys [request response] :as context}]
  (let [auth (get-in request [:headers :authorization])]
    (if-not (= auth (str "Basic " (codec/base64-encode (.getBytes "foo:bar"))))
      (-> context
          terminate
          (assoc-in [:response] (resp/unauthorized)))
      context)))

(sw/defhandler delete-db
  {:summary "Delete db"}
  [_]
  (reset! pet-store {})
  (resp/no-content))

;;;; Routes

(defn annotate
  "Adds metatata m to a swagger route"
  [m]
  (sw.doc/annotate m (interceptor/before ::annotate identity)))

(s/with-fn-validation ;; Optional, but nice to have at compile time
  (sw/defroutes routes
    {:info {:title "Swagger Sample App"
            :description "This is a sample Petstore server."
            :version "2.0"}
     :tags [{:name "pets"
             :description "Everything about your Pets"
             :externalDocs {:description "Find out more"
                            :url "http://swagger.io"}}
            {:name "orders"
             :description "Operations about orders"}]}
    [[["/" ^:interceptors [(body-params/body-params)
                           bootstrap/json-body
                           (sw/body-params)
                           (sw/keywordize-params :form-params :headers)
                           (sw/coerce-params)
                           (sw/validate-response)]
       ["/pets" ^:interceptors [(annotate {:tags ["pets"]})]
        {:get get-all-pets}
        {:post add-pet}
        ["/:id" ^:interceptors [load-pet-from-db]
         {:get get-pet-by-id}
         {:put update-pet}
         {:patch update-pet-with-form}]]
       ["/users"
        {:post add-user}
        ["/:username"
         {:get get-user-by-name}]]
       ["/orders" ^:interceptors [(annotate {:tags ["orders"]})]
        {:post add-order}
        ["/:id" ^:interceptors [load-order-from-db]
         {:get get-order-by-id}]]

       ["/secure" ^:interceptors [basic-auth] {:delete delete-db}]

       ["/doc" {:get [(sw/swagger-doc)]}]
       ["/*resource" {:get [(sw/swagger-ui)]}]]]]))

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/router :linear-search
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port (Integer. (or (System/getenv "PORT") 8080))})

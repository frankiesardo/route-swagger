(ns sample.service
  (:require [pedestal.swagger.core :as swagger]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.impl.interceptor :as interceptor-impl]
            [ring.util.response :refer [response]]
            [schema.core :as s]))

;; Utils

(defn bad-request
  "Returns a Ring response for an HTTP 400 bad request"
  [body]
  {:status  400
   :headers {}
   :body    body})

(defn- keyword-syntax? [s]
  (re-matches #"[A-Za-z*+!_?-][A-Za-z0-9*+!_?-]*" s))

(defn- keyify-params [target]
  (cond
   (map? target) (into {}
                       (for [[k v] target]
                         [(if (and (string? k) (keyword-syntax? k))
                            (keyword k)
                            k)
                          (keyify-params v)]))
   (vector? target) (vec (map keyify-params target))
   :else target))

(interceptor/defon-request wrap-keyword-params
  "Keify form params"
  [request]
  (update-in request [:form-params] keyify-params))

;; Store

(def pet-store (atom {}))

(defn get-pet-by-id [{:keys [path-params] :as req}]
  (response (get-in @pet-store [:pets (:id path-params)])))

(defn update-pet [{:keys [errors path-params json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pet-store assoc-in [:pets (:id path-params)] json-params))))

(defn update-pet-with-form [{:keys [path-params form-params] :as req}]
  (response (swap! pet-store update-in [:pets (:id path-params)] merge form-params)))

(defn get-all-pets [_]
  (response (let [pets (vals (:pets @pet-store))]
              {:total (count pets)
               :pets pets})))

(defn add-pet [{:keys [errors json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pet-store assoc-in [:pets (:id json-params)] json-params))))

;

(defn add-user [{:keys [errors json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pet-store assoc-in [:users (:username json-params)] json-params))))

(defn get-user-by-name [{:keys [path-params] :as req}]
  (response (get-in @pet-store [:users (:username path-params)])))

;;;; Schemas

(def opt s/optional-key)

(s/defschema Category
  {:id s/Int
   :name s/Str})

(s/defschema Tag
  {:id s/Int
   :name s/Str})

(s/defschema Pet
  {:id s/Int
   :name s/Str
   (opt :category) Category
   (opt :tags) [Tag]
   (opt :status) (s/enum "available" "pending" "sold")})

(s/defschema PetList
  {:total s/Int
   :pets [Pet]})

(def PartialPet
  {(opt :name) s/Str
   (opt :status) (s/enum "available" "pending" "sold")})

;

(s/defschema User
  {:username s/Str
   :password s/Str
   (opt :first-name) s/Str
   (opt :last-name) s/Str
   (opt :status) (s/enum "registered" "active" "closed")})

;;;; Routes

(def swagger-docs
  {:title "Swagger Sample App"
   :description "This is a sample Petstore server."
   :apiVersion "1.0"
   :apis [{:route-name ::pet
           :description "Operations about pets"
           :ops [{:route-name ::get-pet-by-id
                  :summary "Find pet by ID"
                  :notes "Returns a pet based on ID"}
                 {:route-name ::update-pet
                  :summary "Update an existing pet"}
                 {:route-name ::update-pet-with-form
                  :summary "Updates a pet in the store with form data"}
                 {:route-name ::get-all-pets
                  :summary "Get all pets in the store"}
                 {:route-name ::add-pet
                  :summary "Add a new pet to the store"}]}
          {:route-name ::user
           :description "Operations about users"
           :ops [{:route-name ::add-user
                  :summary "Create user"}
                 {:route-name ::get-user-by-name
                  :summary "Get user by name"}]}]})

#_(swagger/defroutes routes swagger-docs
  [[:http "localhost" 8080
    ["/" ^:interceptors [(body-params/body-params)
                         wrap-keyword-params
                         bootstrap/json-body]
     ["/pet"
      {:get [^:interceptors [(swagger/post PetList)]
             get-all-pets]}
      {:post [^:interceptors [(swagger/pre {:body Pet})]
              add-pet]}
      ["/:id" ^:interceptors [(swagger/pre {:path {:id s/Int}})]
       {:get [^:interceptors [(swagger/post Pet)]
              get-pet-by-id]}
       {:put [^:interceptors [(swagger/pre {:body Pet})]
              update-pet]}
       {:patch [^:interceptors [(swagger/pre {:form PartialPet})]
                update-pet-with-form]}]]
     ["/user"
      {:post [^:interceptors [(swagger/pre {:body User})]
              add-user]}
      ["/:username" ^:interceptors [(swagger/pre {:path {:username s/Str}})]
       {:get [^:interceptors [(swagger/post User)]
              get-user-by-name]}]]

     ["/ui/*resource" {:get [swagger/swagger-ui]}]
     ["/api-docs" {:get [swagger/resource-listing]}
      ["/pets" {:get [(swagger/api-declaration ::pet)]}]
      ["/user" {:get [(swagger/api-declaration ::user)]}]]]]])

(interceptor/defaround foo
  ([context] (def bar context) context)
  ([context] (def bar context) context))


(interceptor/defbefore aaa
  [{:keys [request] :as context}]
  (def bbb context)
  (if (= "1" (-> request :path-params :a))
    context
    (let [response {:status 404 :body "Not a number"}]
      (-> context
          (interceptor-impl/terminate)
          (assoc :response response)))))

(defn quiz [request] {:status 200 :body "hey"})
(defn quix [request] {:status 201 :body "hoy"})

(swagger/defroutes routes [[
;                            ["/x/:a/:b" ^:interceptors [aaa] {:get quiz}]
                            ["/*resource" ^:interceptors [quiz] {:get quix}]
                            ]])

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

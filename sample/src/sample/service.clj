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

;; Store

(def pet-store (atom {}))

(swagger/defhandler get-pet-by-id
  {:summary "Find pet by ID"
   :description "Returns a pet based on ID"}
  [{:keys [path-params] :as req}]
  (response (get-in @pet-store [:pets (:id path-params)])))

(swagger/defhandler update-pet
  {:summary "Update an existing pet"}
  [{:keys [errors path-params json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pet-store assoc-in [:pets (:id path-params)] json-params))))

(swagger/defhandler update-pet-with-form
  {:summary "Updates a pet in the store with form data"}
  [{:keys [path-params form-params] :as req}]
  (response (swap! pet-store update-in [:pets (:id path-params)] merge form-params)))

(swagger/defhandler get-all-pets
  {:summary "Get all pets in the store"}
  [_]
  (response (let [pets (vals (:pets @pet-store))]
              {:total (count pets)
               :pets pets})))

(swagger/defhandler add-pet
  {:summary "Add a new pet to the store"}
  [{:keys [errors json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pet-store assoc-in [:pets (:id json-params)] json-params))))

;

(swagger/defhandler add-user
  {:summary "Create user"}
  [{:keys [errors json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pet-store assoc-in [:users (:username json-params)] json-params))))

(swagger/defhandler get-user-by-name
  {:summary "Get user by name"}
  [{:keys [path-params] :as req}]
  (response (get-in @pet-store [:users (:username path-params)])))

;;;; Routes

(def swagger-spec
  {:title "Swagger Sample App"
   :description "This is a sample Petstore server."
   :apiVersion "2.0"})

(swagger/defroutes routes
  [[:http "localhost" 8080
    ["/" ^:interceptors [(body-params/body-params)
                         bootstrap/json-body]
     ["/pet"
      {:get get-all-pets}
      {:post add-pet}
      ["/:id" ;;
       {:get get-pet-by-id}
       {:put update-pet}
       {:patch update-pet-with-form}]]
     ["/user"
      {:post add-user}
      ["/:username" ;;
       {:get get-user-by-name}]]

     ["/ui/*resource" {:get swagger/swagger-ui}]
     ["/docs" {:get [(swagger/swagger-object swagger-spec)]}]]]])

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

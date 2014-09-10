(ns sample.service
  (:require [pedestal.swagger.core :as swagger]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :refer [response]]
            [schema.core :as s]))

(defn bad-request
  "Returns a Ring response for an HTTP 400 bad request"
  [body]
  {:status  400
   :headers {}
   :body    body})

(def pets (atom {}))

(defn get-pet-by-id [{:keys [path-params] :as req}]
  (response (get @pets (:id path-params))))

(defn update-pet [{:keys [errors path-params json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! @pets assoc (:id path-params) json-params))))

(defn update-pet-with-form [{:keys [path-params form-params] :as req}]
  (response (swap! @pets update-in (:id path-params) merge form-params)))

(defn get-all-pets [_]
  (response (let [pets (vals @pets)]
              {:total (count pets)
               :pets pets})))

(defn add-pet [{:keys [errors json-params] :as req}]
  (if errors
    (bad-request (pr-str errors))
    (response (swap! pets assoc (:id json-params) json-params))))

;;;;

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

;;;;

(def swagger-docs
  {:title "Swagger Sample App"
   :description "This is a sample Petstore server."
   :apiVersion "1.0"
   :apis [{:route-name ::pets
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
                  :summary "Add a new pet to the store"}]}]})

(swagger/defroutes routes swagger-docs
  [[8080
    ["/" ^:interceptors [(body-params/body-params) bootstrap/json-body]
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

     ["/ui/*resource" {:get [swagger/swagger-ui]}]
     ["/api-docs" {:get [swagger/resource-listing]}
      ["/pets" {:get [(swagger/api-declaration ::pets)]}]]]]])

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

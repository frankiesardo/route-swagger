(ns sample.service
  (:require [pedestal.swagger.core :as swagger]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :refer [response]]
            [schema.core :as s]))



(def pets (atom {}))

(defn add-pet [{:keys [json-params] :as req}]
  (response (swap! pets assoc (:id json-params) json-params)))

(defn find-pet-by-id [{:keys [path-params] :as req}]
  (response (get @pets (:id path-params))))

(defn foo-handler [req]
  (response ((:url-for req) ::pets)))

(s/defschema Child {:z Long})
(s/defschema Foo {:y Boolean})
(s/defschema Pre {:json-body Child
                  :query-params Foo})
(s/defschema Post {:b String})


(def swagger-docs
  {:title "Swagger Sample App"
   :description "This is a sample Petstore server."
   :apiVersion "1.0"
   :apis [{:route-name ::pets
           :description "Operations about pets"
           :ops [{:route-name ::find-pet-by-id
                  :summary "This is home page"
                  :notes "Works 50% of the times"}
                 {:route-name ::add-pet
                  :summary "This is about page"
                  :notes "Works 90% of the times"}]}]})

(swagger/defroutes routes swagger-docs
  [[8080
    ["/" ^:interceptors [(body-params/body-params) bootstrap/json-body]
     ^:interceptors [(swagger/params Pre) (swagger/returns Post)]
     ["/pet" {:post add-pet}
      ["/:id" {:get find-pet-by-id}]]
     ["/api-docs" {:get [swagger/resource-listing]}
      ["/pets" {:get [(swagger/api-declaration ::pets)]}]]]]])

(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

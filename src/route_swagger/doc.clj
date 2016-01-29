(ns route-swagger.doc
  (:require [ring.swagger.swagger2-schema :as spec]
            [ring.swagger.common :refer [deep-merge]]
            [schema.core :as s]
            [plumbing.core :refer [map-vals]]
            [clojure.set :as set]))

(s/defn annotate
  "Attaches swagger documentation to an object"
  [doc :- spec/Operation, obj]
  (vary-meta obj assoc ::doc doc))

(def annotation
  "Gets documentation from an annotated object"
  (comp ::doc meta))

(defn- swagger-json-route? [route]
  (when (= ::swagger-json (:route-name route)) route))

(defn- find-docs [{:keys [interceptors]}]
  (keep annotation interceptors))

(defn- inject-swagger-into-routes [route-table swagger-object]
  (for [route route-table]
    (as-> route route
          (if (swagger-json-route? route)
            (vary-meta route assoc ::swagger-object swagger-object)
            route)
          (if-let [docs (seq (find-docs route))]
            (annotate (apply deep-merge :into docs) route)
            route))))

(defn- documented-handler? [route]
  (-> route :interceptors last annotation))

(defn- ring-keys->swagger [operation]
  (-> operation
      (update :parameters (fn [m] (set/rename-keys m {:query-params :query
                                                      :path-params  :path
                                                      :body-params  :body
                                                      :form-params  :formData
                                                      :headers      :header})))
      (update :responses (fn [m] (map-vals #(set/rename-keys % {:body    :schema}) m)))))

(s/defschema SwaggerPaths
  {s/Str {s/Keyword spec/Operation}})

(s/defn paths :- SwaggerPaths
  "Generates swagger paths from an expanded route table.
  This function can also be used to generate documentation offline or for easy
  debugging (turning schema checks on)."
  [route-table]
  (apply merge-with merge
         (for [{:keys [path method] :as route} route-table
               :let [docs (find-docs route)]
               :when (documented-handler? route)]
           {path {method (ring-keys->swagger (apply deep-merge :into docs))}})))


(defn with-swagger
  "Attaches swagger information as a meta key to each documented route. The
  context passed to each interceptor has a reference to the selected route, so
  information like request and response schemas and the swagger object can be
  retrieved from its meta."
  ([route-table] (with-swagger route-table {:swagger "2.0"
                                            :info    {:title   "Swagger API"
                                                      :version "0.0.1"}}))
  ([route-table docs]
   (let [swagger-object (deep-merge {:paths (paths route-table)} docs)]
     (inject-swagger-into-routes route-table swagger-object))))

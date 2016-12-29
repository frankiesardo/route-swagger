 (ns pedestal.swagger.doc
   (:require [ring.swagger.swagger2-schema :as spec]
             [ring.swagger.common :refer [deep-merge]]
             [schema.core :as s]))

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
            (annotate (apply deep-merge docs) route)
            route))))

(defn- documented-handler? [route]
  (-> route :interceptors last annotation))

(s/defschema Paths
  {s/Str {s/Keyword spec/Operation}})

(s/defn gen-paths :- Paths
  "Generates swagger paths from an expanded route table.
  This function can also be used to generate documentation offline or for easy
  debugging (turning schema checks on)."
  [route-table]
  (apply merge-with merge
         (for [{:keys [path method] :as route} route-table
               :let [docs (find-docs route)]
               :when (documented-handler? route)]
           {path {method (apply deep-merge docs)}})))


(s/defn inject-docs
  "Attaches swagger information as a meta key to each documented route. The
  context passed to each interceptor has a reference to the selected route, so
  information like request and response schemas and the swagger object can be
  retrieved from its meta."
  [docs route-table]
  (let [swagger-object (deep-merge {:swagger "2.0"
                                    :info {:title "Swagger API"
                                           :version "0.0.1"}
                                    :paths (gen-paths route-table)}
                                   docs)]
    (inject-swagger-into-routes route-table swagger-object)))

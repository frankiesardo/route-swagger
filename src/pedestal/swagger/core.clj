(ns pedestal.swagger.core
  (:require [pedestal.swagger.doc :as doc]
            [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :refer [response]]))

;; TODO:
;; wire up swagger-ui
;; responseMessages (map of return codes {:200 S1 :400 S2} ?)
;; application name in route-name. Possibly having more than one api-docs
;; body-params json-params edn-params naming problems
;; better error handling at compile time (e.g. multiple returns)
;; is there a way to determine produces and consumes?
;; auth
;; calculate basePath and resourcePath (use name of api?)


(def example
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



(interceptor/definterceptorfn pre
  "Expect a map in the form of {:query-params S1 :path-params
  S2 :headers S3 :json-body S4} etc. Will assoc them back into request map
  if coercion is successful. Errors are stored under :errors key"
  [pre-schema]
  (with-meta
   (interceptor/on-request
    ::pre
    (fn [req]
      (merge req
             (schema/coerce-params pre-schema req))))
   {::doc/pre pre-schema}))

(interceptor/definterceptorfn post
  ""
  [post-schema]
  (with-meta
   (interceptor/on-response
    ::post
    (fn [{:keys [body] :as resp}]
      (assert (schema/validate post-schema body))
      resp))
   {::doc/post post-schema}))

;;

(def docs {})

(defn- make-handler [key]
  (interceptor/handler key (fn [req]
                             (response (get docs key)))))

(defn api-declaration [route-name]
  (make-handler route-name))

(def resource-listing
  (make-handler ::doc/api-docs))

(def swagger-ui
  ; TODO
  )

(defmacro defroutes [name doc-spec route-spec]
  `(let [route-table# (expand-routes (quote ~route-spec))]
     (def ~name route-table#)
     (alter-var-root #'docs (constantly
                             (doc/expand-docs ~doc-spec route-table#)))))

(ns pedestal.swagger.core
  (:require [pedestal.swagger.doc :as doc]
            [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route.definition :as d :refer [expand-routes]]
            [io.pedestal.http.route.definition.verbose :as v]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http.route :as route]
            [ring.util.response :refer [response resource-response redirect]]))

(def docs {})

(interceptor/definterceptorfn swagger-object
  [doc-spec]
  (with-meta
    (interceptor/handler
     ::doc/swagger-object
     (fn [request]
       (response (::doc/swagger-object docs))))
    {::doc/swagger-object doc-spec}))

(def swagger-ui
  (interceptor/handler
   ::doc/swagger-ui
   (fn [{:keys [path-params path-info url-for] :as req}]
     (let [res (:resource path-params)]
       (case res
         "" (redirect (str path-info "index.html"))
         "conf.js" (response (str "window.API_CONF = {url: \"" (url-for ::doc/swagger-object) "\"};"))
         (resource-response res {:root "swagger-ui/"}))))))

(interceptor/defon-request coerce-params
  [{:keys [request route-name]}]
  (if-let [schema (:pre (docs route-name))]
    (schema/coerce-params schema request)
    request))

(interceptor/defon-response validate-responses
  [{:keys [response route-name]}]
  (if-let [schema (:post (docs route-name))]
    (schema/validate-responses schema response)
    response))

(defmacro defroutes [name route-spec]
  `(let [route-table# (expand-routes (quote ~route-spec))]
     (def ~name route-table#)
     (alter-var-root #'docs (constantly (doc/generate-docs route-table#)))))

;;;;

(defmacro defhandler
  [name doc args & body]
  `(def ~name (with-meta (interceptor/handler (fn ~args ~@body))
                {::doc/handler ~doc})))

(defmacro defmiddleware
  [name doc args & body]
  `(def ~name (with-meta (interceptor/middleware (fn ~args ~@body))
                {::doc/middleware ~doc})))

(defmacro defon-request
  [name doc args & body]
  `(def ~name (with-meta (interceptor/on-request (fn ~args ~@body))
                {::doc/middleware ~doc})))

(defmacro defon-response
  [name doc args & body]
  `(def ~name (with-meta (interceptor/on-response (fn ~args ~@body))
                {::doc/middleware ~doc})))

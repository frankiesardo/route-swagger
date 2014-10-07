(ns pedestal.swagger.core
  (:require [pedestal.swagger.doc :as doc]
            [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :refer [response resource-response redirect]]))

(def docs
  "Holds the generated documentation server by the swagger-object
   endpoint.  It is possible to inspect this var at any time after
   compilation to see the generated documentation."
  {})

(defn- present-swagger-object
  "Todo memoize?"
  []
  docs)

(interceptor/definterceptorfn swagger-object
  "Creates an interceptor that serves the generated documentation on
   the path fo your choice.  Accepts a doc-spec param, which is a map
   that specifies global information about the api (such as :title,
   :description :info etc.) For a complete list visit:
   https://github.com/wordnik/swagger-spec/blob/master/versions/2.0.md"
  [doc-spec]
  (with-meta
    (interceptor/handler
     ::doc/swagger-object
     (fn [request]
       (response (present-swagger-object))))
    {::doc/swagger-object doc-spec}))

(interceptor/defhandler swagger-ui
  "Serves the swagger ui on a path of your choice. Note that the path
  MUST specify a splat argument named \"resource\"
  e.g. \"my-path/*resource\""
  [{:keys [path-params path-info url-for] :as req}]
  (let [res (:resource path-params)]
    (case res
      "" (redirect (str path-info "index.html"))
      "conf.js" (response (str "window.API_CONF = {url: \"" (url-for ::doc/swagger-object) "\"};"))
      (resource-response res {:root "swagger-ui/"}))))

(defn- route-schemas [{:keys [route-name] :as route}]
  (->> docs
       :paths
       (mapcat val)
       (filter #(= (:route-name %) route-name))
       first))

(interceptor/definterceptorfn coerce-params
  "f is a function that accepts a params schema and a request and
   returns a new request.  The no args implementation coerces the
   request with a standard string coercer, assoc an :errors key in the
   response if there are any coercion errors, otherwise deep merges
   back the coercion result into the request (effectively overriding
   the original values with the coerced ones). That function also
   treates the header and query params subchemas as loose schemas, so
   if there are more keys than specified no error will be raised."
  ([] (coerce-params schema/coerce-params))
  ([f]
     (interceptor/before
      (fn [{:keys [request route] :as context}]
        (assoc context :request
               (if-let [schema (->> route route-schemas :parameters)]
                 (f schema request)
                 request))))))

(interceptor/definterceptorfn validate-response
  "f is a function that accepts a responses schema and a response and
   returns a new response.  The no args implementation throws an
   exception if the response model does not match the equivalent model
   for the same status code in the responses schema or the :default
   model if the returned status code could not be matched."
  ([] (validate-response schema/validate-response))
  ([f]
     (interceptor/after
      (fn [{:keys [response route] :as context}]
        (assoc context :response
               (if-let [schema (->> route route-schemas :responses)]
                 (f schema response)
                 response))))))

(defmacro defroutes
  "A drop-in replacement for pedestal's defroutes.  In addition to
  defining a var that hold the expanded routes, compiles the swagger
  documentation contained in the endpoints."
  [name route-spec]
  `(let [route-table# (expand-routes (quote ~route-spec))]
     (def ~name route-table#)
     (alter-var-root #'docs (constantly (doc/generate-docs route-table#)))))

;;;;

(defmacro defhandler
  "Endpoints that want to be recognised as swagger endpoints must be
  defined using this macro."
  [name doc args & body]
  `(def ~name (with-meta (interceptor/handler (fn ~args ~@body))
                {::doc/handler ~doc})))

(defmacro defmiddleware
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name (with-meta (interceptor/middleware ~f1 ~f2)
                  {::doc/middleware ~doc}))))

(defmacro defon-request
  [name doc args & body]
  `(def ~name (with-meta (interceptor/on-request (fn ~args ~@body))
                {::doc/middleware ~doc})))

(defmacro defon-response
  [name doc args & body]
  `(def ~name (with-meta (interceptor/on-response (fn ~args ~@body))
                {::doc/middleware ~doc})))

(defmacro defaround
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name (with-meta (interceptor/middleware ~f1 ~f2)
                  {::doc/middleware ~doc}))))

(defmacro defbefore
  [name doc args & body]
  `(def ~name (with-meta (interceptor/before (fn ~args ~@body))
                {::doc/middleware ~doc})))

(defmacro defafter
  [name doc args & body]
  `(def ~name (with-meta (interceptor/after (fn ~args ~@body))
                {::doc/middleware ~doc})))

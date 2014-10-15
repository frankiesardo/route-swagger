(ns pedestal.swagger.core
  (:require [pedestal.swagger.doc :as doc]
            [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :refer [response resource-response redirect]]))

(interceptor/definterceptorfn swagger-object
  "Creates an interceptor that serves the generated documentation on
   the path fo your choice.  Accepts a doc-spec param, which is a map
   that specifies global information about the api (such as :title,
   :description :info etc.) For a complete list visit:
   https://github.com/wordnik/swagger-spec/blob/master/versions/2.0.md"
  [doc-spec]
  (with-meta
    (interceptor/before
     ::doc/swagger-object
     (fn [{:keys [route] :as context}]
       (assoc context :response (response (meta route)))))
    doc-spec))

(interceptor/definterceptorfn swagger-ui
  "Serves the swagger ui on a path of your choice. Note that the path
  MUST specify a splat argument named \"resource\"
  e.g. \"my-path/*resource\""
  [& path-opts]
  (interceptor/handler
   ::doc/swagger-ui
   (fn [{:keys [path-params path-info url-for] :as request}]
     (let [res (:resource path-params)]
       (case res
         "" (redirect (str path-info "index.html"))
         "conf.js" (response (str "window.API_CONF = {url: \""
                                  (apply url-for ::doc/swagger-object path-opts)
                                  "\"};"))
         (resource-response res {:root "swagger-ui/"}))))))

(interceptor/definterceptorfn coerce-params
  "f is a function that accepts a params schema and a request and
   returns a new request.  The no args implementation coerces the
   request with a standard string coercer, assoc an :errors key in the
   response if there are any coercion errors, otherwise deep merges
   back the coercion result into the request (effectively overriding
   the original values with the coerced ones). That function also
   treates the header and query params subchemas as loose schemas, so
   if there are more keys than specified no error will be raised."
  ([] (coerce-params schema/?bad-request))
  ([f]
     (interceptor/before
      (fn [{:keys [request route] :as context}]
        (if-let [schema (->> route meta ::doc/doc :parameters)]
          (f schema context)
          context)))))

(interceptor/definterceptorfn validate-response
  "f is a function that accepts a responses schema and a response and
   returns a new response.  The no args implementation throws an
   exception if the response model does not match the equivalent model
   for the same status code in the responses schema or the :default
   model if the returned status code could not be matched."
  ([] (validate-response schema/?internal-server-error))
  ([f]
     (interceptor/after
      (fn [{:keys [response route] :as context}]
        (if-let [schemas (->> route meta ::doc/doc :responses)]
          (if-let [schema (or (schemas (:status response)) (schemas :default))]
            (f schema context)
            context)
          context)))))

(defmacro defroutes
  "A drop-in replacement for pedestal's defroutes.  In addition to
  defining a var that hold the expanded routes, compiles the swagger
  documentation contained in the endpoints."
  [name route-spec]
  `(let [route-table# (expand-routes (quote ~route-spec))]
     (def ~name (doc/inject-docs route-table#))))

;;;;

(defmacro defhandler
  [name doc args & body]
  `(def ~name (with-meta (interceptor/handler (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defmiddleware
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name (with-meta (interceptor/middleware ~f1 ~f2)
                  {::doc/doc ~doc}))))

(defmacro defon-request
  [name doc args & body]
  `(def ~name (with-meta (interceptor/on-request (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defon-response
  [name doc args & body]
  `(def ~name (with-meta (interceptor/on-response (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defaround
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name (with-meta (interceptor/around ~f1 ~f2)
                  {::doc/doc ~doc}))))

(defmacro defbefore
  [name doc args & body]
  `(def ~name (with-meta (interceptor/before (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defafter
  [name doc args & body]
  `(def ~name (with-meta (interceptor/after (fn ~args ~@body))
                {::doc/doc ~doc})))

;;

(interceptor/definterceptorfn keywordize-params
  [& ks]
  (interceptor/on-request
   ::keywordize-params
   (fn [request]
     (->> (map (partial get request) ks)
          (map #(zipmap (map keyword (keys %)) (vals %)))
          (zipmap ks)
          (apply merge (apply dissoc request ks))))))

(interceptor/definterceptorfn body-params
  ([] (body-params :json-params :edn-params :transit-params))
  ([& ks]
     (interceptor/on-request
      ::body-params
      (fn [request]
        (->> (map (partial get request) ks)
             (apply merge)
             (assoc request :body-params))))))

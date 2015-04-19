(ns pedestal.swagger.core
  (:require [pedestal.swagger.doc :as doc]
            [pedestal.swagger.schema :as schema]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.interceptor.helpers :as ih]
            [ring.util.response :refer [response resource-response redirect]]
            [ring.swagger.swagger2 :as spec]
            [ring.util.http-status :as status]))

(defn swagger-doc
  "Creates an interceptor that serves the generated documentation on
   the path fo your choice.  Accepts an optional function f that takes
   the swagger-object and returns a ring response."
  ([] (swagger-doc
       (fn [swagger-object]
         (response (spec/swagger-json
                    swagger-object
                    {:default-response-description-fn
                     #(get-in status/status [% :description] "")})))))
  ([f]
   (ih/before
    ::doc/swagger-doc
    (fn [{:keys [route] :as context}]
      (assoc context :response (f (-> route meta ::doc/swagger-doc)))))))

(defn swagger-ui
  "Creates an interceptor that serves the swagger ui on a path of your
  choice. Note that the path MUST specify a splat argument named
  \"resource\" e.g. \"my-path/*resource\". Acceps additional options
  used to construct the swagger-object url (such as :app-name
  :your-app-name), using pedestal's 'path-for'."
  [& path-opts]
  (ih/handler
   ::doc/swagger-ui
   (fn [{:keys [path-params path-info url-for] :as request}]
     (let [res (:resource path-params)]
       (case res
         "" (redirect (str path-info "index.html"))
         "conf.js" (response (str "window.API_CONF = {url: \""
                                  (apply url-for ::doc/swagger-doc path-opts)
                                  "\"};"))
         (resource-response res {:root "swagger-ui/"}))))))

(defn coerce-params
  "Creates an interceptor that coerces the params for the selected
  route, according to the route's swagger documentation. A coercion
  function f that acceps the route params schema and a context and return a
  context can be supplied. The default implementation terminates the
  interceptor chain if any coercion error occurs and return a 400
  response with an explanation for the failure. For more information
  consult 'pedestal.swagger.schema/?bad-request'."
  ([] (coerce-params schema/?bad-request))
  ([f]
   (with-meta
     (ih/before
      (fn [{:keys [route] :as context}]
        (if-let [schema (->> route meta ::doc/doc :parameters)]
          (f schema context)
          context)))
     {::doc/doc {:responses {400 {}}}})))

(defn validate-response
  "Creates an interceptor that validates the response for the selected
  route, according to the route's swagger documentation. A validation
  function f that accepts the route response schema and a context and
  return a context can be supplied. The default implementation will
  substitute the current response with a 500 response and an error
  explanation if a validation error occours. For more information
  consult 'pedestal.swagger.schema/?internal-server-error'."
  ([] (validate-response schema/?internal-server-error))
  ([f]
   (with-meta
     (ih/after
      (fn [{:keys [response route] :as context}]
        (if-let [schemas (->> route meta ::doc/doc :responses)]
          (if-let [schema (or (schemas (:status response))
                              (schemas :default))]
            (f schema context)
            context)
          context)))
     {::doc/doc {:responses {500 {}}}})))

;; Very useful interceptors

(defn body-params
  "Creates an interceptor that will merge the supplied request submaps
  in a single :body-params submaps. This is usually declared after
  'io.pedestal.http.body-params/body-params', so while the first will
  parse the body and assoc it in different submaps (:json-params,
  :edn-params etc.) this interceptor will make sure that the body
  params will always be found under :body-params. By default it will
  merge the request submaps under :json-params, :edn-params and
  :transit-params."
  ([] (body-params :json-params :edn-params :transit-params))
  ([& ks]
     (ih/on-request
      ::body-params
      (fn [request]
        (->> (map (partial get request) ks)
             (apply merge)
             (assoc request :body-params))))))

(defn keywordize-params
  "Creates an interceptor that keywordize the parameters map under the
  specified keys e.g. if you supply :form-params it will keywordize
  the keys in the request submap under :form-params."
  [& ks]
  (ih/on-request
   ::keywordize-params
   (fn [request]
     (->> (map (partial get request) ks)
          (map #(zipmap (map keyword (keys %)) (vals %)))
          (zipmap ks)
          (apply merge (apply dissoc request ks))))))

;;;; Pedestal aliases

(defmacro defhandler
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name (with-meta (ih/handler (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defmiddleware
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name (with-meta (ih/middleware ~f1 ~f2)
                  {::doc/doc ~doc}))))

(defmacro defon-request
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name (with-meta (ih/on-request (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defon-response
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name (with-meta (ih/on-response (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defaround
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name (with-meta (ih/around ~f1 ~f2)
                  {::doc/doc ~doc}))))

(defmacro defbefore
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name (with-meta (ih/before (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defafter
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name (with-meta (ih/after (fn ~args ~@body))
                {::doc/doc ~doc})))

(defmacro defroutes
  "A drop-in replacement for pedestal's defroutes.  In addition to
  defining a var that holds the expanded routes, compiles the swagger
  documentation and injects it into the routes as a meta tag."
  ([name route-spec]
   `(defroutes ~name {} ~route-spec))
  ([name docs route-spec]
   `(let [route-table# (expand-routes (quote ~route-spec))]
      (def ~name (doc/inject-docs ~docs route-table#)))))

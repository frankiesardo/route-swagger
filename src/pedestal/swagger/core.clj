(ns pedestal.swagger.core
  (:require [pedestal.swagger.doc :as doc]
            [pedestal.swagger.schema :as schema]
            [pedestal.swagger.body-params :as body-params]
            [schema.core :as s]
            [io.pedestal.http.route :refer [expand-routes]]
            [io.pedestal.interceptor.helpers :as interceptor]
            [io.pedestal.interceptor :as i]
            [ring.util.response :refer [response resource-response redirect]]
            [ring.swagger.swagger2 :as spec]
            [ring.util.http-status :as status]))

(defn- default-json-converter [swagger-object]
  (spec/swagger-json
   swagger-object
   {:default-response-description-fn
    #(get-in status/status [% :description] "")}))

(defn swagger-json
  "Creates an interceptor that serves the generated documentation on the path of
  your choice. Accepts an optional function f that takes the swagger-object and
  returns a json body."
  ([] (swagger-json default-json-converter))
  ([f]
   (interceptor/before
    ::doc/swagger-json
    (fn [{:keys [route] :as context}]
      (assoc context :response
             {:status 200 :body (f (-> route meta ::doc/swagger-object))})))))

(defn swagger-ui
  "Creates an interceptor that serves the swagger ui on a path of your choice.
  Note that the path MUST specify a splat argument named \"resource\" e.g.
  \"my-path/*resource\". Acceps additional options used to construct the
  swagger-object url (such as :app-name :your-app-name), using pedestal's
  'url-for' syntax."
  [& path-opts]
  (interceptor/handler
   ::doc/swagger-ui
   (fn [{:keys [path-params path-info url-for]}]
     (let [res (:resource path-params)]
       (case res
         "" (redirect (str path-info "index.html"))
         "conf.js" (response (str "window.API_CONF = {url: \""
                                  (apply url-for ::doc/swagger-json path-opts)
                                  "\"};"))
         (resource-response res {:root "swagger-ui/"}))))))

(defn coerce-request
  "Creates an interceptor that coerces the params for the selected route,
  according to the route's swagger documentation. A coercion function f that
  acceps the route params schema and a request and return a request can be
  supplied. The default implementation throws if any coercion error occurs."
  ([] (coerce-request (schema/make-coerce-request)))
  ([f]
   (interceptor/before
    ::coerce-request
    (fn [{:keys [route] :as context}]
      (if-let [schema (->> route doc/annotation :parameters)]
        (update context :request (partial f schema))
        context)))))

(defn validate-response
  "Creates an interceptor that validates the response for the selected route,
  according to the route's swagger documentation. A validation function f that
  accepts the route response schema and a response and return a response can be
  supplied. The default implementation throws if a validation error occours."
  ([] (validate-response (schema/make-validate-response)))
  ([f]
   (interceptor/after
    ::validate-response
    (fn [{:keys [response route] :as context}]
      (let [schemas (->> route doc/annotation :responses)]
        (if-let [schema (or (get schemas (:status response))
                            (get schemas :default))]
          (update context :response (partial f schema))
          context))))))

;;;; Pedestal aliases

(defn body-params
  "An almost drop-in replacement for pedestal's body-params.
  Accepts a parser map with content-type strings as keys instead of regexes.
  Ensures the body keys assoc'd into the request are the ones coerce-request
  expects and keywordizes keys by default."
  ([] (body-params body-params/default-parser-map))
  ([parser-map]
   (doc/annotate
    {:consumes (keys parser-map)}
    (interceptor/before
     ::body-params
     (fn [{:keys [request] :as context}]
       (assoc context :request (body-params/parse-content-type parser-map request)))))))

(defmacro defhandler
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name
     (doc/annotate ~doc (interceptor/handler ~(keyword (str *ns*) (str name)) (fn ~args ~@body)))))

(defmacro defmiddleware
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name
       (doc/annotate ~doc (interceptor/middleware ~(keyword (str *ns*) (str name)) ~f1 ~f2)))))

(defmacro defon-request
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name
     (doc/annotate ~doc (interceptor/on-request ~(keyword (str *ns*) (str name)) (fn ~args ~@body)))))

(defmacro defon-response
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name
     (doc/annotate ~doc (interceptor/on-response ~(keyword (str *ns*) (str name)) (fn ~args ~@body)))))

(defmacro defaround
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc before after]
  (let [f1 (cons 'fn before)
        f2 (cons 'fn after)]
    `(def ~name
       (doc/annotate ~doc (interceptor/around ~(keyword (str *ns*) (str name)) ~f1 ~f2)))))

(defmacro defbefore
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name
     (doc/annotate ~doc (interceptor/before ~(keyword (str *ns*) (str name)) (fn ~args ~@body)))))

(defmacro defafter
  "A drop-in replacement for pedestal's equivalent interceptor. Makes
  it simple to attach a meta tag holding the interceptor swagger
  documentation."
  [name doc args & body]
  `(def ~name
     (doc/annotate ~doc (interceptor/after ~(keyword (str *ns*) (str name)) (fn ~args ~@body)))))

(defmacro defroutes
  "A drop-in replacement for pedestal's defroutes.  In addition to
  defining a var that holds the expanded routes, compiles the swagger
  documentation and injects it into the routes as a meta tag."
  ([name route-spec]
   `(defroutes ~name {} ~route-spec))
  ([name docs route-spec]
   `(let [route-table# (expand-routes (quote ~route-spec))]
      (def ~name (doc/inject-docs ~docs route-table#)))))

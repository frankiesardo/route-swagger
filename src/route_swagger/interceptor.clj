(ns route-swagger.interceptor
  (:require [route-swagger.doc :as doc]
            [route-swagger.schema :as schema]
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
   {:name  ::doc/swagger-json
    :enter (fn [{:keys [route] :as context}]
             (assoc context :response
                            {:status 200 :body (f (-> route meta ::doc/swagger-object))}))}))

(defn swagger-ui
  "Creates an interceptor that serves the swagger ui on a path of your choice.
  Note that the path MUST specify a splat argument named \"resource\" e.g.
  \"my-path/*resource\". Acceps additional options used to construct the
  swagger-object url (such as :app-name :your-app-name), using pedestal's
  'url-for' syntax."
  [& path-opts]
  {:name  ::doc/swagger-ui
   :enter (fn [{:keys [request] :as context}]
            (let [{:keys [path-params path-info url-for]} request
                  res (:resource path-params)]
              (assoc context :response
                             (case res
                               "" (redirect (str path-info "index.html"))
                               "conf.js" (response (str "window.API_CONF = {url: \""
                                                        (apply url-for ::doc/swagger-json path-opts)
                                                        "\"};"))
                               (resource-response res {:root "swagger-ui"})))))})

(defn coerce-request
  "Creates an interceptor that coerces the params for the selected route,
  according to the route's swagger documentation. A coercion function f that
  accepts the route params schema and a request and return a request can be
  supplied. The default implementation throws if any coercion error occurs."
  ([] (coerce-request (schema/make-coerce-request)))
  ([f]
   {:name  ::coerce-request
    :enter (fn [{:keys [route] :as context}]
             (if-let [schema (->> route doc/annotation :parameters)]
               (update context :request (partial f schema))
               context))}))

(defn validate-response
  "Creates an interceptor that validates the response for the selected route,
  according to the route's swagger documentation. A validation function f that
  accepts the route response schema and a response and return a response can be
  supplied. The default implementation throws if a validation error occours."
  ([] (validate-response (schema/make-validate-response)))
  ([f]
   {:name  ::validate-response
    :leave (fn [{:keys [response route] :as context}]
             (let [schemas (->> route doc/annotation :responses)]
               (if-let [schema (or (get schemas (:status response))
                                   (get schemas :default))]
                 (update context :response (partial f schema))
                 context)))}))

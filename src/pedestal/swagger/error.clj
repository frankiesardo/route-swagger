(ns pedestal.swagger.error
  (:require [pedestal.swagger.core :as core]
            [pedestal.swagger.doc :as doc]
            [ring.swagger.middleware :refer [stringify-error]]
            [io.pedestal.interceptor.error :refer [error-dispatch]]))

(def handler
  "Transform swagger errors to sensible REST responses."
  (doc/annotate
   {:responses {400 {} 500 {}}}
   (error-dispatch
    [ctx ex]

    [{:interceptor ::core/body-params}]
    (assoc ctx :response {:status 400
                          :body {:error (format "Badly formatted body for content-type: %s" (-> ctx :request :content-type))}})

    [{:interceptor ::core/coerce-request}]
    (let [error (-> ex ex-data :exception ex-data :error)]
      (assoc ctx :response {:status 400
                            :body (stringify-error error)}))

    [{:interceptor ::core/validate-response}]
    (let [error (-> ex ex-data :exception ex-data :error)]
      (assoc ctx :response {:status 500
                            :body (stringify-error error)}))

    :else (assoc ctx :io.pedestal.impl.interceptor/error ex))))


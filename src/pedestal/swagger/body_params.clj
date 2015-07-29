(ns pedestal.swagger.body-params
  (:require [io.pedestal.http.body-params :as pedestal]
            [ring.middleware.multipart-params :as multipart-params] 
            [clojure.walk :as walk]
            [linked.core :as linked]))

(defn transit-msgpack-parser [request]
  (->> ((pedestal/custom-transit-parser :msgpack) request)
       :transit-params
       (assoc request :body-params)))

(defn transit-json-parser [request]
  (->> ((pedestal/custom-transit-parser :json) request)
       :transit-params
       (assoc request :body-params)))

(defn json-parser [request]
  (->> (pedestal/json-parser request)
       :json-params
       (assoc request :body-params)))

(defn edn-parser [request]
  (->> (pedestal/edn-parser request)
       :edn-params
       (assoc request :body-params)))

(defn urlencoded-parser [request]
  (->> (pedestal/form-parser request)
       :form-params
       walk/keywordize-keys
       (assoc request :form-params)))

(defn multipart-parser [request]
  (->> (multipart-params/multipart-params-request request)
       :multipart-params
       walk/keywordize-keys
       (assoc request :form-params)))

(def default-parser-map
  (linked/map
   "application/json" json-parser
   "application/edn" edn-parser
   "application/transit+json" transit-json-parser
   "application/transit+msgpack" transit-msgpack-parser
   "application/x-www-form-urlencoded" urlencoded-parser
   "multipart/form-data" multipart-parser))

(defn parse-content-type [parser-map request]
  (let [{:keys [content-type] :or {content-type ""}} request
        type (second (re-find #"^(.*?)(?:;|$)" content-type))
        parser (get parser-map type identity)]
    (parser request)))

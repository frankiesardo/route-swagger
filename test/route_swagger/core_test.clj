(ns route-swagger.core-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [route-swagger.doc :as sw.doc]
            [route-swagger.interceptor :as sw.int]
            [schema.core :as s]
            [schema.test :as validation]
            [ring.util.response :refer [response]]
            [ring.swagger.swagger2 :as spec]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.http.body-params :refer [body-params]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.interceptor :as i]
            [scjsv.core :as v]
            [clojure.java.io :as io]
            [ring.swagger.middleware :refer [stringify-error]]
            [clojure.set :as set]))

(use-fixtures :each validation/validate-schemas)

(def req s/required-key)

(def auth-middleware
  (sw.doc/annotate
    {:description "Requires auth as header"
     :parameters  {:headers {(req "auth") s/Str}}}
    (i/interceptor
      {:name  ::auth-middleware
       :enter identity})))

(def id-middleware
  (sw.doc/annotate
    {:description "Requires id on path"
     :parameters  {:path-params {:id s/Int}}}
    (i/interceptor
      {:name  ::id-middleware
       :enter identity})))

(defn non-documented-handler
  [req] (response {}))

(def put-handler
  (sw.doc/annotate
    {:summary    "Put resource with id"
     :parameters {:body-params {:name s/Keyword}}}
    (i/interceptor
      {:name  ::put-handler
       :enter (fn [{{:keys [body-params path-params headers]} :request :as context}]
                (assoc context :response
                               (response {:params (merge body-params path-params
                                                         (select-keys headers ["auth"]))})))})))

(def delete-handler
  (sw.doc/annotate
    {:summary    "Delete resource with id"
     :parameters {:query-params {:notify s/Bool}}
     :operationId "delete-resource"}
    (i/interceptor
      {:name  ::delete-handler
       :enter (fn [{{:keys [query-params path-params headers]} :request :as context}]
                (assoc context :response
                               (response {:params (merge query-params path-params
                                                         (select-keys headers ["auth"]))})))})))

(def get-handler
  (sw.doc/annotate
    {:summary    "Get all resources"
     :parameters {:query-params {:q s/Str}}
     :responses  {200      {:body {:status s/Str}}
                  :default {:body    {:result [s/Str]}
                            :headers {(req "Location") s/Str}}}}
    (i/interceptor
      {:name  ::get-handler
       :enter (fn [{{:keys [query-params]} :request :as context}]
                (assoc context :response
                               (case (:q query-params)
                                 "ok" {:status 200 :body {:status "ok"}}
                                 "created" {:status  201
                                            :body    {:result ["a" "b"]}
                                            :headers {"Location" "Here!"}}
                                 "fail" {:status 299
                                         :body {:result "fail"}
                                         :headers {}})))})))

(def error-catcher
  (i/interceptor
    {:name  ::error-catcher
     :error (fn [context ex]
              (assoc context :response {:status 500 :body (if-let [error (:error (ex-data ex))]
                                                            (stringify-error error)
                                                            {:error (.getMessage ex)})}))}))

(def common-body
  (i/interceptor
    {:name  ::common-body
     :enter (fn [context]
              (update context :request set/rename-keys {:edn-params :body-params}))}))

(defroutes routes
  [["t" :test
    ["/" ^:interceptors [(body-params)
                         common-body
                         error-catcher
                         (sw.int/coerce-request)
                         (sw.int/validate-response)
                         auth-middleware]
     {:get get-handler}
     ["/x/:id" ^:interceptors [id-middleware]
      {:put    put-handler
       :delete delete-handler
       :head   non-documented-handler}]
     ["/doc" {:get [(sw.int/swagger-json)]}]]]])


(deftest generates-correct-paths
  (let [paths {"/"
               {:get
                {:description "Requires auth as header"
                 :summary     "Get all resources"
                 :parameters  {:query  {:q s/Str}
                               :header {(req "auth") s/Str}}
                 :responses   {200      {:schema {:status s/Str}}
                               :default {:schema  {:result [s/Str]}
                                         :headers {(req "Location") s/Str}}}
                 :operationId "get-handler"}}
               "/x/:id"
               {:put
                {:description "Requires id on path"
                 :summary     "Put resource with id"
                 :parameters  {:path   {:id s/Int}
                               :header {(req "auth") s/Str}
                               :body   {:name s/Keyword}}
                 :responses   {}
                 :operationId "put-handler"}
                :delete
                {:description "Requires id on path"
                 :summary     "Delete resource with id"
                 :parameters  {:path   {:id s/Int}
                               :header {(req "auth") s/Str}
                               :query  {:notify s/Bool}}
                 :responses   {}
                 :operationId "delete-resource"}}}]
    (is (= paths (sw.doc/paths routes)))))

(defn make-app [options]
  (-> options
      bootstrap/default-interceptors
      bootstrap/dev-interceptors
      bootstrap/service-fn
      ::bootstrap/service-fn))

(def app (make-app {::bootstrap/router :prefix-tree         ;; or :linear-search
                    ::bootstrap/routes (sw.doc/with-swagger routes
                                                            {:title   "Test"
                                                             :version "0.1"})}))

(deftest generates-valid-json-schema
  (let [validator (v/validator (slurp (io/resource "ring/swagger/swagger-schema.json")))]
    (validator (spec/swagger-json {:paths (sw.doc/paths routes)}))))

(deftest coerces-params
  (are [resp req] (= resp (read-string (:body req)))
                  {:params {"auth" "y", :id 1, :notify true}}
                  (response-for app :delete "http://t/x/1?notify=true" :headers {"Auth" "y"})

                  {:error {:headers {"auth" "missing-required-key"}}}
                  (response-for app :delete "http://t/x/1?notify=true")

                  {:error {:query-params "missing-required-key"}}
                  (response-for app :delete "http://t/x/1" :headers {"Auth" "y"})

                  {:error {:path-params {:id "(not (integer? W))"}}}
                  (response-for app :delete "http://t/x/W?notify=true" :headers {"Auth" "y"})

                  {:error {:body-params {:name "(not (keyword? 3))"}}}
                  (response-for app :put "http://t/x/1"
                                :headers {"Auth"         "y"
                                          "Content-Type" "application/edn"}
                                :body (pr-str {:name 3}))

                  {:params {"auth" "y", :id 1, :name :foo}}
                  (response-for app :put "http://t/x/1"
                                :headers {"Auth"         "y"
                                          "Content-Type" "application/edn"}
                                :body (pr-str {:name "foo"}))))

(deftest validates-response
  (are [resp req] (= resp (read-string (:body req)))
                  {:status "ok"}
                  (response-for app
                                :get "http://t/?q=ok"
                                :headers {"Auth" "y"})

                  {:error {:query-params {:q "missing-required-key"}}}
                  (response-for app
                                :get "http://t/?z=yes"
                                :headers {"Auth" "y"})

                  {:result ["a" "b"]}
                  (response-for app
                                :get "http://t/?q=created"
                                :headers {"Auth" "y"})

                  {:error {:headers {"Location" "missing-required-key"}
                           :body    {:result "(not (sequential? \"fail\"))"}}}
                  (response-for app
                                :get "http://t/?q=fail"
                                :headers {"Auth" "y"})))

(deftest checks-swagger-handler-like-any-other-route
  (are [resp req] (= resp (read-string (:body req)))
                  {:error {:headers {"auth" "missing-required-key"}}}
                  (response-for app :get "http://t/doc")))

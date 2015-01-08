(ns pedestal.swagger.core-test
  (:require [pedestal.swagger.core :refer :all]
            [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [schema.core :as s]
            [pedestal.swagger.doc :as doc]
            [io.pedestal.http.route.definition :as definition]
            [io.pedestal.http.body-params :as pedestal-body-params]
            [io.pedestal.http :as bootstrap]))

(defon-request auth-middleware
  {:description "Requires auth as header"
   :parameters {:header {:auth s/Str}}}
  [req] req)

(defon-request id-middleware
  {:description "Requires id on path"
   :parameters {:path {:id s/Int}}}
  [req] req)

(defhandler put-handler
  {:summary "Put resource with id"
   :parameters {:body {:name s/Keyword}}}
  [{:keys [body-params path-params headers]}]
  {:status 200 :headers {}
   :body {:params (merge body-params path-params
                         (select-keys headers [:auth]))}})

(defhandler delete-handler
  {:summary "Delete resource with id"
   :parameters {:query {:notify s/Bool}}}
  [{:keys [query-params path-params headers]}]
  {:status 200 :headers {}
   :body {:params (merge query-params path-params
                         (select-keys headers [:auth]))}})

(defn non-documented-handler
  [req] {:satus 200})

(defhandler get-handler
  {:summary "Get all resources"
   :parameters {:query {:q s/Str}}
   :responses {200 {:schema {:status s/Str}}
               400 {:schema {:error s/Any}}
               :default {:schema {:result [s/Str]}
                         :headers {"Location" s/Str}}}}
  [{:keys [query-params]}]
  (case (:q query-params)
    "ok" {:status 200 :body {:status "ok"} :headers {}}
    "created" {:status 201 :body {:result ["a" "b"]} :headers {"Location" "Here!"}}
    "fail" {:status 299 :body {:result "fail"} :headers {}}))

(def info
  {:title "Test"
   :version "0.1"})

(defn make-app [options]
  (-> options
      bootstrap/default-interceptors
      bootstrap/service-fn
      ::bootstrap/service-fn))

(definition/defroutes routes
  [["t" :test
    ["/" ^:interceptors [(pedestal-body-params/body-params)
                         (keywordize-params :headers) (body-params)
                         (coerce-params) (validate-response)
                         auth-middleware]
     {:get get-handler}
     ["/:id" ^:interceptors [id-middleware]
      {:put put-handler
       :delete delete-handler
       :head non-documented-handler}]
     ["/doc" {:get [(swagger-doc)]}]]]])

(deftest generates-correct-documentation
  (let [expected {:swagger "2.0"
                  :info info
                  :paths
                  {"/"
                   {:get {:description "Requires auth as header"
                          :summary "Get all resources"
                          :parameters {:query {:q s/Str}
                                       :header {:auth s/Str}}
                          :responses {200 {:schema {:status s/Str}}
                                      400 {:schema {:error s/Any}}
                                      :default {:schema {:result [s/Str]}
                                                :headers {"Location" s/Str}}}}}
                   "/:id"
                   {:put {:description "Requires id on path"
                          :summary "Put resource with id"
                          :parameters {:path {:id s/Int}
                                       :header {:auth s/Str}
                                       :body {:name s/Keyword}}}
                    :delete {:description "Requires id on path"
                             :summary "Delete resource with id"
                             :parameters {:path {:id s/Int}
                                          :header {:auth s/Str}
                                          :query {:notify s/Bool}}}}}}]
    (is (= expected (doc/generate-docs info routes)))))

(def app (make-app {::bootstrap/routes (doc/inject-docs info routes)}))

(deftest coerces-params
  (are [resp req] (= resp (read-string (:body req)))
       {:params {:auth "y", :id 1, :notify true}}
       (response-for app :delete "http://t/1?notify=true" :headers {"Auth" "y"})

       {:error {:headers {:auth "missing-required-key"}}}
       (response-for app :delete "http://t/1?notify=true")

       {:error {:query-params {:notify "missing-required-key"}}}
       (response-for app :delete "http://t/1" :headers {"Auth" "y"})

       {:error {:path-params {:id "(not (integer? W))"}}}
       (response-for app :delete "http://t/W?notify=true" :headers {"Auth" "y"})

       {:error {:body-params {:name "(not (keyword? 3))"}}}
       (response-for app :put "http://t/1"
                     :headers {"Auth" "y"
                               "Content-Type" "application/edn"}
                     :body (pr-str {:name 3}))

       {:params {:auth "y", :id 1, :name :foo}}
       (response-for app :put "http://t/1"
                     :headers {"Auth" "y"
                               "Content-Type" "application/edn"}
                     :body (pr-str {:name "foo"}))))

(deftest validates-response
  (are [status resp req] (and (= status (:status req))
                              (= resp (read-string (:body req))))
       200 {:status "ok"}
       (response-for app :get "http://t/?q=ok" :headers {"Auth" "y"})

       400 {:error {:query-params {:q "missing-required-key"}}}
       (response-for app :get "http://t/" :headers {"Auth" "y"})

       201 {:result ["a" "b"]}
       (response-for app :get "http://t/?q=created" :headers {"Auth" "y"})

       500 {:error {:headers {"Location" "missing-required-key"}
                    :body {:result "(not (sequential? \"fail\"))"}}}
       (response-for app :get "http://t/?q=fail" :headers {"Auth" "y"})))

(deftest checks-swagger-handler-like-any-other-route
  (are [resp req] (= resp (read-string (:body req)))
       {:error {:headers {:auth "missing-required-key"}}}
       (response-for app :get "http://t/doc")))

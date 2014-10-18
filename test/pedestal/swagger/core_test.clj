(ns pedestal.swagger.core-test
  (:require [pedestal.swagger.core :refer :all]
            [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [schema.core :as s]
            [pedestal.swagger.doc :as doc]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.http.body-params :as pedestal-body-params]
            [io.pedestal.http :as bootstrap]))

(defon-request id-middleware
  {:description "Requires id and auth"
   :parameters {:path {:id s/Int}
                :header {:auth s/Str}}}
  [req] req)

(defhandler put-handler
  {:summary "Put resource with id"
   :parameters {:body {:name s/Keyword}}}
  [{:keys [body-params path-params headers]}]
  {:status 200 :headers {}
   :body {:params (merge body-params path-params (select-keys headers [:auth]))}})

(defhandler delete-handler
  {:summary "Delete resource with id"
   :parameters {:query {:notify s/Bool}}}
  [{:keys [query-params path-params headers]}]
  {:status 200 :headers {}
   :body {:params (merge query-params path-params (select-keys headers [:auth]))}})

(defhandler get-handler
  {:summary "Get all resources"
   :parameters {:query {:q s/Str}}
   :responses {200 {:schema {:status s/Str}}
               400 {:schema {:error s/Any}}
               :default {:schema {:result [s/Str]}
                         :headers ["Location"]}}}
  [{:keys [query-params]}]
  (case (:q query-params)
    "ok" {:status 200 :body {:status "ok"} :headers {}}
    "created" {:status 201 :body {:result ["a" "b"]} :headers {"Location" "Here!"}}
    "fail" {:status 299 :body {:result "fail"} :headers {}}))

(def doc-spec
  {:title "Test"})

(defn make-app [options]
  (-> options
      bootstrap/default-interceptors
      bootstrap/service-fn
      ::bootstrap/service-fn))

(defroutes routes
  [["t" :test
    ["/" ^:interceptors [(pedestal-body-params/body-params)
                         (keywordize-params :headers) (body-params)
                         (coerce-params) (validate-response)]
     {:get get-handler}
     ["/:id" ^:interceptors [id-middleware]
      {:put put-handler
       :delete delete-handler}]
     ]]])

(def app (make-app {::bootstrap/routes routes}))

(deftest generates-corret-documentation
  (let [{:keys [paths info]} (doc/swagger-object routes)]
    (is (= doc-spec info))
    (is (= {"/" #{{:route-name ::get-handler
                   :method :get
                   :parameters {:query {:q s/Str}}
                   :responses {200 {:schema {:status s/Str}}
                               400 {:schema {:error s/Any}}
                               :default {:schema {:result [s/Str]}
                                         :headers ["Location"]}}}}
            "/:id" #{{:route-name ::put-handler
                      :method :put
                      :parameters {:path {:id s/Int}
                                   :header {:auth s/Str}
                                   :body {:name s/Keyword}}}
                     {:route-name ::delete-handler
                      :method :delete
                      :parameters {:path {:id s/Int}
                                   :header {:auth s/Str}
                                   :query {:notify s/Bool}}}}}
           (into {} (for [[path operations] paths]
                      [path (set (for [op operations]
                                   (select-keys op [:route-name
                                                    :method
                                                    :parameters
                                                    :responses])))]))))))


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
       (response-for app :get "http://t/?q=ok")

       400 {:error {:query-params {:q "missing-required-key"}}}
       (response-for app :get "http://t/")

       201 {:result ["a" "b"]}
       (response-for app :get "http://t/?q=created")

       500 {:error {:headers {"Location" "missing-required-key"}
                    :body {:result "(not (sequential? \"fail\"))"}}}
       (response-for app :get "http://t/?q=fail")))

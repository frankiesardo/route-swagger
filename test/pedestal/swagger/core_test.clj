(ns pedestal.swagger.core-test
  (:require [pedestal.swagger.core :refer :all]
            [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [schema.core :as s]
            [pedestal.swagger.doc :as doc]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.http.body-params :as pedestal-body-params]
            [io.pedestal.http :as bootstrap]))

(def ok
  {:status 200
   :body {:status "ok"}
   :headers {}})

(defon-request id-middleware
  {:description "Requires id and auth"
   :parameters {:path {:id s/Int}
                :header {:auth s/Str}}}
  [req] req)

(defhandler put-handler
  {:summary "Put resource with id"
   :parameters {:body {:name s/Keyword}}}
  [_] ok)

(defhandler delete-handler
  {:summary "Delete resource with id"
   :parameters {:query {:notify s/Bool}}}
  [_] ok)

(defhandler get-handler
  {:summary "Get all resources"
   :parameters {:query {:q s/Str}}
   :responses {200 {:schema {:status s/Str}}
               400 {:schema {:error s/Any}}
               :default {:schema {:result [s/Str]}
                         :headers [:h]}}}
  [{:keys [query-params]}]
  (case (:q query-params)
    "ok" ok
    "created" {:status 201 :body {:result ["a" "b"]} :headers {:h 4}}
    "fail" {:status 299 :body {:result "fail"} :headers {}}))

(def doc-spec
  {:title "Test"})

(defn make-app [options]
  (-> options
      bootstrap/default-interceptors
      bootstrap/service-fn
      ::bootstrap/service-fn))

(defroutes routes
  [["t"
    ["/" ^:interceptors [(pedestal-body-params/body-params)
                         (keywordize-params :headers) (body-params)
                         (coerce-params) (validate-response)]
     {:get get-handler}
     ["/:id" ^:interceptors [id-middleware]
      {:put put-handler
       :delete delete-handler}]
     ["/docs" {:get [(swagger-object doc-spec)]}]]]])

(def app (make-app {::bootstrap/routes routes}))

(deftest generates-corret-documentation
  (let [{:keys [paths title]} docs]
    (is (= "Test" title))
    (is (= {"/" #{{:route-name ::get-handler
                   :method :get
                   :parameters {:query {:q s/Str}}
                   :responses {200 {:schema {:status s/Str}}
                               400 {:schema {:error s/Any}}
                               :default {:schema {:result [s/Str]}
                                         :headers [:h]}}}}
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
       {:status "ok"}
       (response-for app :delete "http://t/1?notify=true" :headers {"Auth" "y"})

       {:error {:headers {:auth "missing-required-key"}}}
       (response-for app :delete "http://t/1?notify=true")

       {:error {:query-params "missing-required-key"}}
       (response-for app :delete "http://t/1" :headers {"Auth" "y"})

       {:error {:path-params {:id "(not (integer? W))"}}}
       (response-for app :delete "http://t/W?notify=true" :headers {"Auth" "y"})

       {:error {:body-params {:name "(not (keyword? 3))"}}}
       (response-for app :put "http://t/1"
                     :headers {"Auth" "y"
                               "Content-Type" "application/edn"}
                     :body (pr-str {:name 3}))

       {:status "ok"}
       (response-for app :put "http://t/1"
                     :headers {"Auth" "y"
                               "Content-Type" "application/edn"}
                     :body (pr-str {:name "foo"}))))

(deftest validates-response
  (are [status resp req] (and (= status (:status req))
                              (= resp (read-string (:body req))))
       200 {:status "ok"}
       (response-for app :get "http://t/?q=ok")

       400 {:error {:query-params "missing-required-key"}}
       (response-for app :get "http://t/")


       201 {:result ["a" "b"]}
       (response-for app :get "http://t/?q=created")

       500 {:error {:headers {:h "missing-required-key"}
                    :body {:result "(not (sequential? \"fail\"))"}}}
       (response-for app :get "http://t/?q=fail")))

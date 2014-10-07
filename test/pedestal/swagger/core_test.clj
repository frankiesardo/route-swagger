(ns pedestal.swagger.core-test
  (:require [pedestal.swagger.core :refer :all]
            [clojure.test :refer :all]
            [schema.core :as s]
            [pedestal.swagger.doc :as doc]
            [io.pedestal.http.route.definition :refer [expand-routes]]))

(def ok-response
  {:status 200
   :body ""})

(defhandler handler1
  {:description "Handler 1"
   :parameters {:path {:id s/Str}}
   :responses {400 s/Str}}
  [_] ok-response)

(defhandler handler2
  {:description "Handler 2"
   :parameters {:header {:id s/Int}}
   :responses {:default s/Int}}
  [_] ok-response)

(def doc-spec
  {:title "Test"})

(deftest sample-anonymous-app
  (let [routes (expand-routes
                `[[["/a/:b/c" {:post handler1}]
                   ["/x/:y/z" {:patch handler2}]
                   ["/docs" {:get [(swagger-object doc-spec)]}]]])
        {:keys [paths] :as docs} (doc/generate-docs routes)]
    (testing "Produces correct documentation"
      (is (= "Test" (:title docs)))
      (is (= {"/a/:b/c" [{:route-name ::handler1
                          :method :post
                          :parameters {:path {:id s/Str}}
                          :responses {400 s/Str}}]
              "/x/:y/z" [{:route-name ::handler2
                          :method :patch
                          :parameters {:header {:id s/Int}}
                          :responses {:default s/Int}}]}
             (into {} (for [[path operations] paths]
                        [path (for [op operations]
                                (select-keys op [:route-name
                                                 :method
                                                 :parameters
                                                 :responses]))])))))))

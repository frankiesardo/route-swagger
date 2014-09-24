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
   :params {:path {:id s/Str}}
   :responses {:default s/Str}}
  [_] ok-response)

(defhandler handler2
  {:description "Handler 2"
   :params {:path {:id s/Str}}
   :responses {:default s/Str}}
  [_] ok-response)

(def doc-spec
  {:title "Test"})

(deftest sample-anonymous-app
  (let [routes (expand-routes
                 '[[["/a/:b/c" {:post handler1}]
                    ["/x/:y/z" {:patch handler2}]
                    ["/docs" {:get [(swagger-object doc-spec)]}]]])
        docs (doc/generate-docs routes)]
    (is (= "Test" (:title docs)))
    (is (= 2 (count (:operations docs))))))

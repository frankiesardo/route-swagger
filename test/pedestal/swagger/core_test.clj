(ns pedestal.swagger.core-test
  (:require [pedestal.swagger.core :refer :all]
            [clojure.test :refer :all]
            [pedestal.swagger.doc :as doc]
            [io.pedestal.http.route.definition :refer [expand-routes]]))

(def ok-response
  {:status 200
   :body ""})

(def handler1
  (constantly ok-response))

(def handler2
  (constantly ok-response))

(deftest sample-anonymous-app
  (let [docs {:apis [{:route-name ::handlers
                      :ops [{:route-name ::handler1}
                            {:route-name ::handler2}]}]}
        routes (expand-routes
                '[[["/a/:b/c" {:post handler1}]
                   ["/x/:y/z" {:patch handler2}]
                                        ;
                   ["/1/2/3" {:get [resource-listing]}
                    ["/7/8/9" {:get [(api-declaration ::handlers)]}]]]])
        {:keys [::doc/swagger-ui
                ::doc/api-docs
                ::handlers]} (doc/expand-docs docs routes)]
    (is (= "/1/2/3" swagger-ui))
    (is (= "/7/8/9" (-> api-docs :apis first :path)))
    (is (= ["/a/{b}/c" "/x/{y}/z"] (->> handlers :apis (map :path))))))

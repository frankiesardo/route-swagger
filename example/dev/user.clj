(ns user
  (:require [clojure.tools.namespace.repl :as repl]))

(defn dev []
  (require 'dev)
  (in-ns 'dev)
  #_ (dev/start))

(def refresh repl/refresh)

(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]))

(defn dev []
  (require 'dev)
  (in-ns 'dev)
  #_ (dev/start))

(defproject route-swagger "Build with boot"
  :dependencies
  [[metosin/ring-swagger "0.26.2"]
   [metosin/ring-swagger-ui "3.36.0"]]
  :profiles
  {:dev {:dependencies
         [[org.clojure/clojure "1.10.2"]
          [io.pedestal/pedestal.service "0.5.8"]
          [io.pedestal/pedestal.jetty "0.5.8"]
          [metosin/scjsv "0.6.1" :exclusions [org.clojure/core.async]]]}})

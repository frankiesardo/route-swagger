(defproject route-swagger
  "0.1.0-SNAPSHOT"
  :dependencies
  [[org.clojure/clojure "1.7.0"]

   [metosin/ring-swagger "0.22.3"]
   [metosin/ring-swagger-ui "2.1.4-0"]]
  :source-paths ["src" "resources"]
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.service "0.4.0"]
                                  [io.pedestal/pedestal.jetty "0.4.0"]
                                  [metosin/scjsv "0.2.0" :exclusions [org.clojure/core.async]]]}})

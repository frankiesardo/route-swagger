(defproject route-swagger "Build with boot"
  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [metosin/ring-swagger "0.24.3"]
   [metosin/ring-swagger-ui "3.0.17"]]
  :source-paths ["src" "resources"]
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.service "0.5.3"]
                                  [io.pedestal/pedestal.jetty "0.5.3"]
                                  [metosin/scjsv "0.4.0" :exclusions [org.clojure/core.async]]]}})

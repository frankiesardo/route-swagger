(defproject route-swagger "Build with boot"
  :dependencies
  [[org.clojure/clojure "1.7.0"]
   [metosin/ring-swagger "0.22.3"]
   [metosin/ring-swagger-ui "2.1.4-0"]]
  :source-paths ["src" "resources"]
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.service "0.5.2"]
                                  [io.pedestal/pedestal.jetty "0.5.2"]
                                  [metosin/scjsv "0.2.0" :exclusions [org.clojure/core.async]]]}})

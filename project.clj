(defproject pedestal-swagger "0.1.0"
  :description "Swagger documentation for Pedestal routes"
  :url "http://github.com/frankiesardo/pedestal-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[codox "0.8.10"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.pedestal/pedestal.service "0.3.0"]
                 [prismatic/schema "0.3.0"]
                 [metosin/ring-swagger "0.12.0"]
                 [metosin/ring-swagger-ui "2.0.17"]]
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.1.5"]]}})

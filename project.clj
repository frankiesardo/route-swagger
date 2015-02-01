(defproject frankiesardo/pedestal-swagger "0.1.0-SNAPSHOT"
  :description "Swagger documentation for Pedestal routes"
  :url "http://github.com/frankiesardo/pedestal-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[codox "0.8.10"]]
  :codox {:src-dir-uri "http://github.com/frankiesardo/pedestal-swagger/blob/master/"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.pedestal/pedestal.service "0.3.0"]
                 [metosin/ring-swagger "0.17.1-SNAPSHOT"]
                 [metosin/ring-swagger-ui "2.1.0-alpha.6-SNAPSHOT"]]
  :min-lein-version "2.0.0"
  :deploy-repositories {"snapshots" {:url "https://clojars.org/repo"
                                     :username [:gpg :env/clojars_username]
                                     :password [:gpg :env/clojars_password]}}
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.3.0"]]}})

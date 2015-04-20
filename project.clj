(defproject frankiesardo/pedestal-swagger "0.4.0-SNAPSHOT"
  :description "Swagger documentation for Pedestal routes"
  :url "http://github.com/frankiesardo/pedestal-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  ;:plugins [[codox "0.8.11"]]
  ;:codox {:src-dir-uri "http://github.com/frankiesardo/pedestal-swagger/blob/master/"
  ;        :src-linenum-anchor-prefix "L"}

  :plugins [[s3-wagon-private "1.1.2"]]

  :repositories  [["nu-maven" {:url "s3p://nu-maven/releases/"
                               :username [:gpg :env/artifacts_aws_access_key_id]
                               :passphrase [:gpg :env/artifacts_aws_secret_access_key]}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.pedestal/pedestal.service "0.4.0"]
                 [metosin/ring-swagger "0.19.5"]
                 [metosin/ring-swagger-ui "2.1.1-M1"]]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["doc"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v"]
                  ["deploy" "clojars"]
                  ["rsync" "doc/" "gh-pages"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles {:dev {:dependencies [[io.pedestal/pedestal.jetty "0.4.0"]]}})

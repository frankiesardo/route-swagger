(defn env [n]
  (System/getenv n))

(merge-env!
 :dependencies '[[org.clojure/clojure "1.6.0"]
                 [io.pedestal/pedestal.service "0.3.0"]
                 [metosin/ring-swagger "0.18.0"]
                 [metosin/ring-swagger-ui "2.1.1-M1"]]
 :repositories {"clojars" {:url "https://clojars.org/repo/"
                           :username (env "CLOJARS_USERNAME")
                           :password (env "CLOJARS_PASSWORD")}}
 :source-paths #{"src"}
 :test-paths   #{"test"})

(def version "0.3.1-SNAPSHOT")

(task-options!
 pom {:project 'frankiesardo/pedestal-swagger
      :version version
      :description "Swagger documentation for Pedestal routes"
      :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}}
 push {:repo "clojars"})



;; == Testing tasks ========================================

(deftask with-test
  "Add test to source paths"
  []
  (set-env! :source-paths #(clojure.set/union % (get-env :test-paths)))
  identity)

;; Include test/ in REPL sources
(replace-task!
  [r repl] (fn [& xs] (with-test) (apply r xs)))

(require '[clojure.test :refer [run-tests]])

(deftask test
  "Run project tests"
  []
  (with-test)
  (set-env! :dependencies #(conj % '[org.clojure/tools.namespace "0.2.10"]))
  (set-env! :dependencies #(conj % '[io.pedestal/pedestal.jetty "0.3.0"]))
  (require '[clojure.tools.namespace.find :refer [find-namespaces-in-dir]])
  (let [find-namespaces-in-dir (resolve 'clojure.tools.namespace.find/find-namespaces-in-dir)
        test-nses              (->> (get-env :test-paths)
                                    (mapcat #(find-namespaces-in-dir (clojure.java.io/file %)))
                                    distinct)]
    (doall (map require test-nses))
    (apply clojure.test/run-tests test-nses)))


;; == CI tasks ========================================

(require '[clojure.java.shell :only [sh]]
         '[boot.git :as git])

(task-options!
 push {:gpg-user-id (env "GPG_USER_ID")
       :gpg-keyring (str (env "HOME") "/.gnupg/secring.asc")
       :gpg-passphrase (env "GPG_PASSPHRASE")})

(defn null-task [] identity)

(defn last-commit [] (sh "git" "log" "--oneline" "-1"))

(deftask promote
  []
  ;; drop -SNAPSHOT from build.boot
  (null-task))

(deftask bump
  []
  ;; increase version + -SNAPSHOT
  (null-task))

(deftask doc
  []
  (null-task))

(deftask tag
  []
  (sh "git" "tag" "-a" (str "v" version) "-m" version)
  (null-task))

(deftask commit
  []
  (sh "git" "commit" "-m" version)
  (null-task))

(deftask ->master
  []
  (sh "git" "push" "origin" "master" "--quiet")
  (null-task))

(deftask ->gh-pages
  "Push doc directory to gh-pages branch"
  []
  (sh "git" "clone" "-b" "gh-pages" (env "$REPO_URL") "gh-pages")
  (sh "rsync" "-a" "--exclude=checkouts" "doc/" "gh-pages/")
  (sh "cd" "gh-pages")
  (sh "add" ".")
  (sh "git" "commit" "-m" (last-commit))
  (sh "git" "push" "origin" "gh-pages" "--quiet")
  (sh "cd" "..")
  (null-task))

(deftask ->heroku
  "Push sample directory to heroku branch"
  []
  (sh "git" "clone" "-b" "heroku" (env "$REPO_URL") "heroku")
  (sh "rsync" "-a" "--exclude=checkouts" "sample/" "heroku/")
  (sh "cd" "heroku")
  (sh "add" ".")
  (sh "git" "commit" "-m" (last-commit))
  (sh "git" "push" "origin" "heroku" "--quiet")
  (sh "cd" "..")
  (null-task))

(deftask ->clojars
 [_ release bool]
 (comp (pom) (jar) (push :gpg-sign release)))

(deftask snapshot
  []
  (comp (->clojars) (->heroku)))

(deftask release
  []
  (comp (promote) (doc) (commit) (tag) (->clojars :release true) (bump) (commit) (->master) (->gh-pages)))

(deftask deploy
  []
  (if (= "false" (env "TRAVIS_PULL_REQUEST"))
    (if (.contains (last-commit) "[ci release]")
      (release)
      (snapshot))
    (null-task)))

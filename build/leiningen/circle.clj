(ns leiningen.circle
  (:require [leiningen.core [eval :as eval]]
            [leiningen
             [rsync :as rsync]
             [release :as release]
             [deploy :as deploy]]))

(defn env [s]
  (System/getenv s))

(defn circle [project & args]
  (condp re-find (env "CIRCLE_BRANCH")
    #"master"
    (do (deploy/deploy project "clojars")
        (rsync/rsync project "sample/" "heroku"))

    #"(?i)release"
    (do
      (eval/sh "git" "reset" "--hard" "origin/master")
      (release/release project)
      (eval/sh "git" "push" "origin" "--delete" (env "CIRCLE_BRANCH")))))

(ns buddylist.core
    (:require [org.httpkit.server :as http-kit]
              [duratom.core :as duratom]))

(duratom :local-file
         :file-path "/home/ubuntu/BuddyList/data.duratom"
         :init {:x 1 :y 2})

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed: " status)))
    (on-receive channel (fn [data] ;; echo it back
                          (send! channel data)))))

(defn -main [& args]
  (let [p 8000]
    (http-kit/run-server handler {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))
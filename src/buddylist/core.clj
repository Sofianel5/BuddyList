(ns buddylist.core
    (:require [org.httpkit.server :as http-kit]
              [duratom.core :as duratom]
              [compojure.route :only [files not-found]]
              [compojure.core :only [defroutes GET POST DELETE ANY context]]))

(duratom :local-file
         :file-path "/home/ubuntu/BuddyList/data.duratom"
         :init {:x 1 :y 2})

(defn render [req]
  (println (pr-str req))
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello World"})

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed: " status)))
    (on-receive channel (fn [data] ;; echo it back
                          (send! channel data)))))

(defroutes all-routes
  (GET "/" [] render)
  (GET "/ws" [] handler)     ;; websocket
  (files "/static/") ;; static file url prefix /static, in `public` folder
  (not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args]
  (let [p 8000]
    (http-kit/run-server all-routes {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))
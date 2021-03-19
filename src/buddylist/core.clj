(ns buddylist.core
  (:require [org.httpkit.server :as http-kit]
            [buddylist.users :as users]
            [compojure.core :refer :all]
            [compojure.route :as route]))

(defn render [req]
  (println (pr-str req))
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "Hello World"})

(defn sign-up [req]
  (let [new-user (users/create-user! (-> req :params :username) (-> req :params :cleartext-password) (-> req :params :phone))]
    {:status 201
     :body {"user" new-user}})) ;; Aaron: Should I return a map or stringified json?

(defn handler [request]
  (let [channel "handler"]
    (http-kit/with-channel request channel
      (http-kit/on-close channel (fn [status] (println "channel closed: " status)))
      (http-kit/on-receive channel (fn [data] ;; echo it back
                                     (http-kit/send! channel data))))))

(defroutes all-routes
  (GET "/" [] render)
  (GET "/ws" [] handler)     ;; websocket
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args]
  (let [p 8000]
    (http-kit/run-server all-routes {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))
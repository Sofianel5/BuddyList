(ns buddylist.core
  (:require [buddylist.users :as users]
            [cheshire.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :as http-kit]
            [ring.util.response :as response]))

(defn render-index [req]
  (println (pr-str req))
  (-> (response/response "Hello World")
      (response/content-type "text/plain")))

(defn render-sign-up [req]
  (let [{:keys [username cleartext-password phone]} (:params req)
        user (users/create-user! username cleartext-password phone)]
    (if user (-> user
                 json/generate-string
                 response/response
                 (response/content-type "application/json")
                 (response/charset "utf-8")
                 (response/status 201))
        (-> {:status "failed"}
            json/generate-string
            response/response
            (response/content-type "application/json")
            (response/charset "utf-8")
            (response/status 400)))))

(comment
  (render-sign-up {:params {:username "test"
                            :cleartext-password "password"
                            :phone "555-555-5555"}})
  (render-sign-up {:params {:username "test2"
                            :cleartext-password "password"
                            :phone "555-555-5555"}}))

(defn render-log-in [req]
  (let [{:keys [username password]} (:params req)
        user (users/get-auth-token username password)]
    (if user (-> user
                 json/generate-string
                 response/response
                 (response/content-type "application/json")
                 (response/charset "utf-8")
                 (response/status 200))
        (-> {:status "failed"}
            json/generate-string
            response/response
            (response/content-type "application/json")
            (response/charset "utf-8")
            (response/status 400)))))

(comment
  (render-log-in {:params {:username "sofiane" :password "password"}})
  (render-log-in {:params {:username "sofiane" :password "wrongpassword"}})
)


(defn ws-handler [req]
  (let [channel "handler"]
    (http-kit/with-channel req channel
      (http-kit/on-close channel (fn [status] (println "channel closed: " status)))
      (http-kit/on-receive channel (fn [data] ;; echo it back
                                     (http-kit/send! channel data))))))

(defroutes all-routes
  (GET "/" [] render-index)
  (POST "/signup" [] render-sign-up)
  (GET "/ws" [] ws-handler)
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args]
  (let [p 8000]
    (http-kit/run-server #'all-routes {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))

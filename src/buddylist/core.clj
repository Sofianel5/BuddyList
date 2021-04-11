(ns buddylist.core
  (:require [buddylist.users :as users]
            [cheshire.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :as http-kit]
            [ring.util.response :as response]))

(def 📲 (atom {}))

;; Helper utils -------------------------

(defn plaintext-response [body]
  (-> (response/response body)
      (response/content-type "text/plain")
      (response/charset "utf-8")))

(defn json-response [body & [status]]
  (-> body
      json/generate-string
      response/response
      (response/content-type "application/json")
      (response/charset "utf-8")
      (response/status (or status 200))))

;;---------------------------------------

(defn render-index [req]
  (println (pr-str req))
  (plaintext-response "Hello World"))

(defn render-sign-up [req]
  (println (pr-str req))
  (let [{:keys [username cleartext-password phone]} (-> req :params)
        user (users/create-user! username cleartext-password phone)]
    (print user)
    (if user
      (json-response user 201)
      (json-response {:status "failed"} 400))))

(comment
  (render-sign-up {:params {:username "test"
                            :cleartext-password "password"
  :phone "555-555-5555"}})
  (render-sign-up {:params {:username "bruhhhhh"
                            :cleartext-password "password"
                            :phone "555-565-5555"}}))

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
  (render-log-in {:params {:username "sofiane" :password "wrongpassword"}}))

(defn render-set-status [req]
  (let [{:keys [username new-status]} (:params req)
        auth-token (-> req :headers :Authorization)
        user (users/authenticate-user username auth-token)]
    (if user (-> (users/set-status! username new-status)
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
  (render-set-status {:params {:username "sofiane" :new-status "Still coding BuddyList"} :headers {:Authorization "9509c9ac-5bed-4597-8a56-54d262fa8457"}}))

(defn on-receive-message [data req]
  (let [parsed-data (json/parse-string data)
        message (-> parsed-data :data :message)
        to (-> parsed-data :data :to)
        from (-> parsed-data :data :to)
        auth-token (-> req :headers :Authorization)
        user (users/authenticate-user from auth-token)]
    ;; Should I send back the entire convo history or just the recent message? Will clients have to save records locally? What if a new client connects?
    (if user (let [sent-message (users/send-message! from to message)]
               (-> sent-message
                   json/generate-string
                   response/response
                   (response/content-type "application/json")
                   (response/charset "utf-8")
                   (response/status 201))
               (http-kit/send! (get @📲 to) sent-message))
        (-> {:status "failed"}
            json/generate-string
            response/response
            (response/content-type "application/json")
            (response/charset "utf-8")
            (response/status 400)))))
;; I want to write a macro for these functions since they're all of form gather data->check if authenticated->authentication branch

(defn chat-handler [req]
  (http-kit/with-channel req channel
    (swap! 📲 assoc (-> req :data :from) channel)
    (http-kit/on-receive channel #(on-receive-message % req))
    (http-kit/on-close channel (fn [_]
                                 ;; I don't really like this, what if they're still connected through a different websocket (ie. status)
                                 (swap! 📲 dissoc (-> req :data :from))))))

;; Not sure how I should implement this (as HTTP vs WebSocket)
(defn render-get-buddies-status [req] (print req))

(defn changelast [l f]
  (concat (drop-last l) (-> l last f list)))

(defmacro wrapmiddleware [middleware endpoints]
  (reverse (into () (map #(changelast % (apply comp middleware)) endpoints))))

(defroutes all-routes
  ;; How do I write a macro that wraps each function (the last element of each list below) in a call to wrap-json-body
  (GET "/" [] render-index)
  (POST "/signup" [] render-sign-up)
  (GET "/chat" [] chat-handler)
  (POST "/set-status" [] render-set-status)
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args]
  (let [p 8000];; What is the point of #' - Clojure docs say that its a "var quote" but I don't know why we need to call var on a function that's already defined
    (http-kit/run-server #'all-routes {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))

(ns buddylist.core
  (:require [buddylist.users :as users]
            [cheshire.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :as http-kit]
            [ring.util.response :as response]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            ;[ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.session :refer [wrap-session]]))

; Should I persist this?
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

(defn clean-user [user-obj]
  (dissoc user-obj :password-hash))

(defn get-auth-token [req]
  (-> req :headers (get "authorization")))

(defn get-request-user [req]
  (-> req :headers (get "request-user")))

(defn save-client [req channel]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)]
    (if user
      (if (contains? @📲 user)
        (swap! 📲 assoc username (conj (get @📲 username) channel))
        (swap! 📲 assoc username (list channel))))))

(defn remove-client [req channel]
  (let [auth-token (get-auth-token req)
        username (-> req :data :from)
        user (users/authenticate-user username auth-token)]
    (if user
      (if (contains? @📲 user)
        (swap! 📲 assoc username (remove #(= channel %) (get @📲 username)))))))

(defn notify-user [user message]
  (println "notifying" user)
  (if-let [channels (get @📲 user)]
    (->> channels (map #(http-kit/send! % message)) doall)
    (println "cannot assoc"))
  (println "done"))

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
      (json-response (clean-user user) 201)
      (json-response {:status "failed"} 400))))

(comment
  (render-sign-up {:params {:username "test"
                            :cleartext-password "password"
                            :phone "555-555-5555"}})
  (render-sign-up {:params {:username "bruhhhh"
                            :cleartext-password "password"
                            :phone "555-565-5555"}}))

(defn render-log-in [req]
  (let [_ (println req)
        {:keys [username password]} (:params req)
        user (users/get-auth-token username password)]
    (if user
      (json-response (clean-user user) 200)
      (json-response {:status "failed"} 400))))

(comment
  (render-log-in {:params {:username "bruhhhh" :password "password"}})
  (render-log-in {:params {:username "sofiane" :password "wrongpassword"}}))

(defn render-update-user [req]
  (let [auth-token (get-auth-token req)
        request-user (get-request-user req)
        auth-result (users/authenticate-user request-user auth-token)]
    (if auth-result
      (json-response (clean-user auth-result) 200)
      (json-response {:status "failed"} 400))))

(defn render-set-status [req]
  (let [{:keys [username new-status]} (:params req)
        auth-token (get-auth-token req)
        user (users/authenticate-user username auth-token)]
    (if user
      (json-response (clean-user (users/set-status! username new-status)) 201)
      (json-response {:status "failed"} 400))))

(comment
  (render-set-status {:params {:username "sofiane" :new-status "Still coding BuddyList"} :headers {:Authorization "2b6f0364-a2f8-443f-a358-9e80d6d8c159"}}))

(defn on-receive-message [data req]
  (println "on-receive-message\n")
  (println "Data: " data "\n\n")
  (println "Req: " req)
  (let [data (json/parse-string data true)
        _ (println data "\n\n")
        message (:message data)
        to (-> req :params :with-user)
        from (get-request-user req)
        _ (println message to from "\n\n")
        auth-token (get-auth-token req)
        _ (println auth-token "\n\n")
        user (users/authenticate-user from auth-token)
        _ (println user "\n\n")]
    ;; Should I send back the entire convo history or just the recent message? Will clients have to save records locally? What if a new client connects?
    (if user
      (let [sent-message (users/send-message! from to message)
            encoded-message (json/generate-string sent-message)]
        (println "succeeded")
        (if-let [clients (get @📲 to)] (doall (map #(http-kit/send! % encoded-message) clients)))
        (if-let [clients (get @📲 from)] (doall (map #(http-kit/send! % encoded-message) clients))))
      (println "failed"))))
;; I want to write a macro for these functions since they're all of form gather data->check if authenticated->authentication branch

(comment
  (on-receive-message "{\"to\": \"liam\", \"from\": \"sofiane\", \"message\": \"hello there\"}" {:headers {"authorization" "2b6f0364-a2f8-443f-a358-9e80d6d8c159"}}))

(defn send-recent-messages! [req channel]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        with-user (-> req :params :with-user)
        user (users/authenticate-user username auth-token)]
    (if user (let [convo-history (users/get-recent-messages username with-user 25)
                   encoded (json/generate-string convo-history)]
               (http-kit/send! channel encoded)))))

(defn chat-handler [req]
  (http-kit/with-channel req channel
    ;; Turned into map<name->list<channel>> to support multiple clients
    (print req)
    (save-client req channel) ; security issue here do not want to notify unauthorized clients
    (send-recent-messages! req channel)
    (http-kit/on-receive channel #(on-receive-message % req))
    (http-kit/on-close channel (fn [_]
                                 ;; Does this disconnect other websockets from the same IP (ie. status)
                                 (remove-client req channel)))))

(defn get-chat-history [req]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)]
    (if user 
      (let [req-data (-> req :data (json/generate-string true))
            buddy (:buddy req-data)
            {:keys [start offset] :or {start 0 offset 25}} req-data
            convo-history (users/get-convo username buddy start offset)]
        (json-response convo-history))
      (json-response {:status "failed"} 400))))

(defn send-buddies! [req channel]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)]
    (if user (http-kit/send! channel (json/generate-string (users/get-buddies username))))))

(defn notify-status-change [username new-status]
  (let [buddies (users/get-buddies username)]
    (notify-user username (json/generate-string {:new-status new-status}))
    (->> buddies
         (map #(future (notify-user (:username %) (json/generate-string (users/get-buddies (:username %))))))
         doall
         (keep deref))))

(defn on-receive-status-update [req data] 
  (println "received update" data)
  (println "clients " @📲)
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)
        new-status (-> data (json/parse-string true) :new-status)]
    (if user (let [user (users/set-status! (:username user) new-status)]
      (notify-status-change username new-status)))))

(defn buddylist-handler [req] 
  (http-kit/with-channel req channel
                         (println req "\n\n")
                         (println channel "\n\n")
                         (save-client req channel)
                         (send-buddies! req channel)
                         (http-kit/on-receive channel #(on-receive-status-update req %))
                         (http-kit/on-close channel (fn [_] remove-client req channel))))

(defn render-create-buddies [req]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        new-buddy (-> req :params :new-buddy)
        user (users/authenticate-user username auth-token)]
    (if user
      (if-let [_ (users/create-buddies! username new-buddy)]
        (json-response {:status "succeeded"} 200)
        (json-response {:status "failed"} 400)))))

(defroutes all-routes
  ;; How do I write a macro that wraps each function (the last element of each list below) in a call to wrap-json-body
  (GET "/" [] render-index)
  (POST "/signup" [] render-sign-up)
  (POST "/login" [] render-log-in)
  (POST "/user" [] render-update-user)
  (GET "/chat" [] chat-handler)
  (GET "/chat-history" [] get-chat-history)
  (POST "/add-buddy" [] render-create-buddies)
  (GET "/buddies" [] buddylist-handler)
  (POST "/set-status" [] render-set-status)
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(def app (-> all-routes 
             ring.middleware.keyword-params/wrap-keyword-params
             ring.middleware.params/wrap-params
             ;ring.middleware.anti-forgery/wrap-anti-forgery
             ring.middleware.session/wrap-session))
(defn -main [& args]
  (let [p 8000];; What is the point of #' - Clojure docs say that its a "var quote" but I don't know why we need to call var on a function that's already defined
    (http-kit/run-server #'app {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))
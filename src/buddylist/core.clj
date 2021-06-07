(ns buddylist.core
  (:require [buddylist.users :as users]
            [cheshire.core :as json]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [org.httpkit.server :as http-kit]
            [ring.util.response :as response]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            ;[ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.json :refer [wrap-json-body]]
            [nrepl.server :as nrepl-server]
            refactor-nrepl.middleware
            [cider.nrepl :as cider-nrepl]
            [slingshot.slingshot :refer :all]
            [try-let :refer :all]))

(println "rerunning core")

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

(defn save-client [req channel-name channel]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)]
    (if user
      (swap! 📲 assoc-in [username channel-name] channel))))

(defn remove-client [req channel-name]
  (let [auth-token (get-auth-token req)
        username (-> req :data :from)
        user (users/authenticate-user username auth-token)]
    (if user
      (if (contains? @📲 user)
        (swap! 📲 assoc username (dissoc (get 📲 username) channel-name))))))

(defn notify-user [user channel-name message]
  (println "notifying" user)
  (if-let [channel (-> @📲 (get user) (get channel-name))]
    (http-kit/send! channel message)
    (println "cannot assoc"))
  (println "done"))

;;---------------------------------------

(defn render-index [req]
  (println (pr-str req))
  (plaintext-response "Hello World"))

(defn render-sign-up [req]
  (println (pr-str req))
  (try+-let [{:keys [username cleartext-password phone email first-name last-name]} (-> req :params)
             user (users/create-user! username cleartext-password phone email first-name last-name)]
            (json-response (clean-user user) 201)
            (catch Object _
              (json-response (:object &throw-context) 400))))

(defn render-log-in [req]
  (let [_ (println req)
        {:keys [username password]} (:params req)
        user (users/get-auth-token username password)]
    (if user
      (json-response (clean-user user) 200)
      (json-response {:status "failed"} 400))))

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

(defn on-receive-message [data req]
  (println "on-receive-message\n")
  (println "Data: " data "\n\n")
  (println "Req: " req)
  (let [data (json/parse-string data true)
        _ (println data "\n\n")
        message (:message data)
        to (:to data)
        from (get-request-user req)
        _ (println message to from "\n\n")
        auth-token (get-auth-token req)
        _ (println auth-token "\n\n")
        user (users/authenticate-user from auth-token)
        _ (println user "\n\n")]
    ;; Should I send back the entire convo history or just the recent message? Will clients have to save records locally? What if a new client connects?
    ;; TODO: Also verify that the "to" is in the user's buddylist
    (if user
      (let [sent-message (users/send-message! from to message)
            encoded-message (json/generate-string sent-message)]
        (println "succeeded")
        (if-let [recipient-client (-> @📲 (get to) (get "chat"))] (http-kit/send! recipient-client encoded-message))
        (if-let [sender-client (-> @📲 (get from) (get "chat"))] (http-kit/send! sender-client encoded-message)))
      (println "failed"))))
;; I want to write a macro for these functions since they're all of form gather data->check if authenticated->authentication branch

(comment
  (let [from "liam"
        to "sofiane"
        message "test as liam 8"
        sent-message (users/send-message! from to message)
        encoded-message (json/generate-string sent-message)]
    (println "succeeded")
    (if-let [recipient-client (-> @📲 (get to) (get "chat"))] (http-kit/send! recipient-client encoded-message) (println "cannot assoc recpipent"))
    (if-let [sender-client (-> @📲 (get from) (get "chat"))] (http-kit/send! sender-client encoded-message) (println "cannot assoc sender"))))

(defn chat-handler [req]
  (http-kit/with-channel req channel
    ;; Turned into map<name->list<channel>> to support multiple clients
    (print req)
    (save-client req "chat" channel) ; security issue here do not want to notify unauthorized clients
    (http-kit/on-receive channel #(on-receive-message % req))
    (http-kit/on-close channel (fn [_]
                                 ;; Does this disconnect other websockets from the same IP (ie. status)
                                 (remove-client req "chat")))))

(defn get-chat-history [req]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)]
    (if user
      (let [buddy (-> req :params :buddy)
            {:keys [start offset] :or {start 0 offset 25}} (:params req)
            [start offset] (map #(Integer/parseInt %) [start offset])
            convo-history (users/get-convo username buddy start offset)]
        (println "history:" convo-history)
        (json-response convo-history))
      (json-response {:status "failed"} 400))))

(defn send-buddies! [req channel]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)]
    (if user (http-kit/send! channel (json/generate-string (users/get-buddies username))))))

(defn notify-status-change [username new-status]
  (let [buddies (users/get-buddies username)]
    (notify-user username "buddylist" (json/generate-string {:new-status new-status}))
    (->> buddies
         (map #(future (notify-user (:username %) "buddylist" (json/generate-string (users/get-buddies (:username %))))))
         doall
         (keep deref))))

(defn on-receive-status-update [req data]
  (println "received update" data)
  (println "clients " @📲)
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        user (users/authenticate-user username auth-token)
        new-status (-> data (json/parse-string true) :new-status)]
    (if user (let [_ (users/set-status! (:username user) new-status)]
               (notify-status-change username new-status)))))

(comment
   (let [new-status "some really long status just to see how the client will render it. \nsome really long status just to see how the client will render it."
         username "liam"
         _ (users/set-status! username new-status)]
     (notify-status-change username new-status)))

(defn buddylist-handler [req]
  (http-kit/with-channel req channel
    (println req "\n\n")
    (println channel "\n\n")
    (save-client req "buddylist" channel)
    (send-buddies! req channel)
    (http-kit/on-receive channel #(on-receive-status-update req %))
    (http-kit/on-close channel (fn [_] (remove-client req "buddylist")))))

(defn render-create-buddies [req]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)
        new-buddy (-> req :params :new-buddy)
        user (users/authenticate-user username auth-token)]
    (if user
      (if-let [_ (users/create-buddies! username new-buddy)]
        ;; TODO: Notify receiver on websocket
        (do
          (notify-user username "buddylist" (json/generate-string (users/get-buddies username)))
          (notify-user (:username new-buddy) "buddylist" (json/generate-string (users/get-buddies (:username new-buddy))))
          (json-response {:status "succeeded"} 200))
        (json-response {:status "failed"} 400)))))

(defn render-set-pfp [req]
  (let [auth-token (get-auth-token req)
        username (get-request-user req)]
    (if-let [_ (users/authenticate-user username auth-token)]
      (let [user (users/set-profile-picture username (-> req :params :image :tempfile))]
        (notify-status-change (:username user) (:status user))
        (json-response (clean-user user) 201))
      (json-response {:status "failed"} 400))))

(defn render-rearrange-buddies [req]
  (println req)
  (println (-> req :body :new-buddies-order) (type (-> req :body :new-buddies-order)))
  (let [auth-token (get-auth-token req)
        username (get-request-user req)]
    (if-let [_ (users/authenticate-user username auth-token)]
      (if-let [new-buddies-order (-> req :body :new-buddies-order)]
        (if-let [user (users/rearrange-buddies username new-buddies-order)]
          (json-response (clean-user user) 201)
          (json-response {:status "failed" :reason "error setting"} 400))
        (json-response {:status "failed" :reason "no new buddies order"} 400))
      (json-response {:status "failed" :reason "invalid credentials"} 400))))

(defroutes all-routes
  (GET "/" [] render-index)
  (POST "/signup" [] render-sign-up)
  (POST "/login" [] render-log-in)
  (POST "/user" [] render-update-user)
  (GET "/chat" [] chat-handler)
  (GET "/chat-history" [] get-chat-history)
  (POST "/add-buddy" [] render-create-buddies)
  (GET "/buddies" [] buddylist-handler)
  (POST "/set-status" [] render-set-status)
  (POST "/set-pfp" [] render-set-pfp)
  (POST "/rearrange-buddies" [] render-rearrange-buddies)
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(def app (-> all-routes
             ring.middleware.keyword-params/wrap-keyword-params
             ring.middleware.params/wrap-params
             ring.middleware.multipart-params/wrap-multipart-params
             (ring.middleware.json/wrap-json-body {:keywords? true})
             ;ring.middleware.anti-forgery/wrap-anti-forgery
             ring.middleware.session/wrap-session
             ring.middleware.reload/wrap-reload))

(defn start-repl! []
  (let [handler (apply nrepl-server/default-handler
                       (map resolve
                            (concat cider-nrepl/cider-middleware
                                    '[refactor-nrepl.middleware/wrap-refactor])))]
    (nrepl-server/start-server :bind "0.0.0.0"
                               :port 4004
                               :handler handler)))


(defn -main [& args]
  (start-repl!)
  (let [p 8000]
    (http-kit/run-server #'app {:ip "0.0.0.0", :port p})
    (println "Server running on port" p)))
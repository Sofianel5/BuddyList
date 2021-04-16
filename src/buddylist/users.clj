(ns buddylist.users
  (:require [clojure.java.io :as io]
            [duratom.core :as duratom]
            [java-time])
  (:import org.mindrot.jbcrypt.BCrypt))

(def users (duratom/duratom :local-file
                            :file-path (doto (io/file "./data/users.duratom")
                                         (io/make-parents))
                            :init {}))

(def buddies (duratom/duratom :local-file
                              :file-path (doto (io/file "./data/buddies.duratom")
                                           (io/make-parents))
                              :init {})) ;; Map of #{user-a user-b}, <Messages>[]

(defn hashpw [raw]
  (BCrypt/hashpw raw (BCrypt/gensalt 12)))

(defn checkpw [raw hashed]
  (boolean (BCrypt/checkpw raw hashed)))

(defn gen-auth-token []
  (.toString (java.util.UUID/randomUUID)))

;; returns new user map
;; TODO: Validate username, password, phone
(defn create-user! [username cleartext-password phone]
  ;; Is there a quick way to do not contains? besides wrapping it in a not
  ;; Should I return as [failure_reason user] with one being nil or like this? Ideally there should be a failure message.
  (if (contains? @users username) nil (let [user {:username username
                                                  :password-hash (hashpw cleartext-password)
                                                  :phone phone
                                                  :buddies []
                                                  :auth-token (gen-auth-token)
                                                  :status nil}]
                                        (swap! users assoc username user)
                                        user)))

(defn delete-user! [username]
  (swap! buddies (fn [x] (into {} (filter #(contains? % username) x))))
                     (swap! users dissoc username))

;; TODO: might be nice to support a set of auth tokens so user can be logged in from
;; multiple clients. 
;;(Question: is it bad if multiple clients to use the same auth token?)
(defn set-auth-token! [username token]
  (swap! users assoc-in [username :auth-token] token))

(defn get-auth-token [username password]
  (let [user (get @users username)
        auth (checkpw password (:password-hash user))]
    (if auth user nil)))

;; TODO: Do nothing if buddies exist
(defn create-buddies! [username-one username-two]
  (if (contains? @buddies #{username-one username-two}) nil (swap! buddies assoc #{username-one username-two} [])))

(defn remove-buddies! [username-one username-two]
  (swap! buddies dissoc #{username-one username-two}))

(defn set-status! [username status]
  (get (swap! users assoc-in [username :status] status) username))

(defn send-message! [from to message]
  ;; Is there a difference here between assoc and assoc-in 
  (-> (swap! buddies assoc-in [#{from to}] (conj (get @buddies #{from to}) {:from from
                                                                            :to to
                                                                            :time (.toString (java-time/local-date-time))
                                                                            :message message}))
      (get #{from to})
      first))

(defn get-buddies [username]
  (->> @buddies keys (filter #(contains? % username)) (map (fn [pair] (first (filter #(not= % username) pair))))))

(defn get-convo [username buddy start offset]
  (-> @buddies (get #{username buddy}) (subvec start (+ start offset))))

(comment
  (def username "sofiane")
  (keys @buddies)
  (def fakebuddies '(#{"liam" "sofiane"} #{"sofiane" "fake1"} #{"sofiane" "fake2"}))
  (->> fakebuddies (filter #(contains? % username)) (map (fn [pair] (first (filter #(not= % username) pair)))))
  (filter #(contains? % username) (keys @buddies))
  (get-buddies "sofiane"))

(defn authenticate-user [username auth-token]
  (let [assoc-user (get @users username)]
    (if (= auth-token (:auth-token assoc-user)) assoc-user nil)))

(comment
  (set-auth-token! "sofiane" (gen-auth-token))
  users
  (.toString (java-time/local-date-time)))

(comment
  (create-user! "sofiane" "password" "9179570254")
  (create-user! "liam" "password" "9179570254")
  (-> @users (get "sofiane") :auth-token)
  (if (authenticate-user "sofiane" "9509c9ac-5bed-4597-8a56-54d262fa8457") true false)
  (create-buddies! "liam" "sofiane")
  @buddies
  (remove-buddies! "liam" "sofiane")
  (delete-user! "sofiane")
  (delete-user! nil)
  (set-status! "sofiane" "Coding BuddyList")
  (send-message! "sofiane" "liam" "Did u finish beatstreet?")
  (send-message! "liam" "sofiane" "Yes! Do you want to see?")
  @users)

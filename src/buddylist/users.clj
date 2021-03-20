(ns buddylist.users
  (:require [duratom.core :as duratom])
  (:import [java.security MessageDigest]))

(def users (duratom/duratom :local-file
                            :file-path "/home/ubuntu/BuddyList/users.duratom"
                            :init {}))
(def buddies (duratom/duratom :local-file
                              :file-path "/home/ubuntu/BuddyList/buddies.duratom"
                              :init []))

(defn sha256 [string]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes string "UTF-8"))]
    (apply str (map (partial format "%02x") digest))))

(defn gen-auth-token []
  (sha256 (str 1)))

(defn create-user! [username cleartext-password phone]
  (get (swap! users assoc username {:username username
                                    :password-hash (sha256 cleartext-password)
                                    :phone phone
                                    :buddies []
                                    :auth-token ""}) username))

(defn delete-user! [username]
  (swap! users dissoc username)
  (swap! buddies filterv (fn [x] (= (some #(= % username) x) nil))))

(defn create-buddies! [username-one username-two]
  (swap! buddies conj [username-one username-two]))

(defn remove-buddies! [username-one username-two]
  (swap! buddies (fn [x] filterv #(not= [username-one username-two] %) x)))

(comment
  (create-user! "sofiane" "password" "9179570254")
  (create-user! "liam" "password" "9179570254")
  (-> @users (get "sofiane") :username)
  (create-buddies! "liam" "sofiane")
  @buddies
  (remove-buddies! "liam" "sofiane")
  (delete-user! "sofiane")
  @users)

(defn set-auth-token! [username token]
  (assoc (get users username) :auth-token token))
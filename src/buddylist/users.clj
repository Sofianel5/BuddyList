(ns buddylist.users
  (:require [duratom.core :as duratom])
  (:import [java.security MessageDigest]))

(def data (duratom/duratom :local-file
                   :file-path "/home/ubuntu/BuddyList/data.duratom"
                   :init {}))

(defn sha256 [string]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes string "UTF-8"))]
    (apply str (map (partial format "%02x") digest))))

(sha256 "hello")

(defn gen-auth-token []
  (sha256 (str 1)))

(defn create-user! [username cleartext-password phone]
  (get (assoc data username {:username username
                             :password-hash (sha256 cleartext-password)
                             :phone phone
                             :buddies []
                             :auth-token ""}) username))

(defn delete-user! [username])

(defn set-auth-token! [username token]
  (assoc (get data username) :auth-token token))
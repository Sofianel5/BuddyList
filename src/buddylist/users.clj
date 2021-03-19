(ns buddylist.users
    (:require [duratom.core :as duratom]))

(import java.security.MessageDigest)

(def data (duratom :local-file
         :file-path "/home/ubuntu/BuddyList/data.duratom"
         :init {}))

(defn sha256 [string]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes string "UTF-8"))]
    (apply str (map (partial format "%02x") digest))))

(defn create-user! [username cleartext-password phone]
    (assoc username {
        :username username
        :password-hash (sha256 cleartext-password)
        :phone phone
    }))
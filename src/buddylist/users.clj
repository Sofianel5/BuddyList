(ns buddylist.users
    (:require [duratom.core :as duratom]))

(def data (duratom :local-file
         :file-path "/home/ubuntu/BuddyList/data.duratom"
         :init []))

(defn create-user! [username cleartext-password phone]
    ())
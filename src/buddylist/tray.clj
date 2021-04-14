(ns buddylist.tray
  (:import [java.awt Taskbar Toolkit]
           [java.awt SystemTray Toolkit TrayIcon])
  (:require [clojure.java.io :as io]))

(Taskbar/isTaskbarSupported)

(System/getenv "CLASSPATH")
(let [t (Taskbar/getTaskbar)]
  (.setIconImage t (.getImage (Toolkit/getDefaultToolkit)
                              "/Users/sofiane/Documents/BuddyList/src/buddylist/IconTemplate.png")))
(def icon (doto (TrayIcon. (.getImage (Toolkit/getDefaultToolkit)
                                      "/Users/sofiane/Documents/BuddyList/src/buddylist/IconTemplate.png"))
            (.setImageAutoSize true)))

(.add (SystemTray/getSystemTray) icon)
(ns bk.core
  (:require [reagent.dom :as rdom]
            [bk.public.page :as page]
            [bk.admin.shell :as admin]
            [bk.admin.state :as state]
            [bk.supabase :as db]))

(defn- admin-path? []
  (clojure.string/starts-with? (.-pathname js/location) "/admin"))

(defn- boot-admin []
  (db/on-auth-change
   (fn [session]
     (reset! state/session session)
     (if session
       (do (db/fetch-role #(reset! state/role %))
           (when (= @state/screen :login)
             (reset! state/screen :inquiries)))
       (do (reset! state/role nil)
           (reset! state/screen :login)))))
  (db/get-session
   (fn [session]
     (reset! state/session session)
     (if session
       (do (db/fetch-role #(reset! state/role %))
           (reset! state/screen :inquiries))
       (reset! state/screen :login)))))

(defn init []
  (if (admin-path?)
    (do
      (boot-admin)
      (rdom/render [admin/shell] (.getElementById js/document "app")))
    (rdom/render [page/root] (.getElementById js/document "app"))))

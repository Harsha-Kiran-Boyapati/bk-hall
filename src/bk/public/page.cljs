(ns bk.public.page
  (:require [bk.public.inquiry :as inquiry]
            [bk.public.location :as location]))

(defn footer []
  [:footer {:style {:background "var(--dark)" :color "rgba(255,255,255,0.6)"
                    :text-align "center" :padding "32px 24px" :font-size "14px"}}
   [:p {:style {:margin-bottom "8px" :font-weight "600" :color "#fff"}} "BK Function Hall"]
   [:p "© 2026 BK Function Hall. All rights reserved."]])

(defn root []
  [:div {:style {:min-height "100vh" :display "flex" :flex-direction "column"
                 :background "var(--dark)"}}
   [:div {:style {:flex "1"}}
    [inquiry/section]
    [location/section]]
   [footer]])

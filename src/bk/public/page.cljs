(ns bk.public.page
  (:require [bk.public.hero :as hero]
            [bk.public.gallery :as gallery]))

(defn root []
  [:div
   [hero/section]
   [gallery/section]])

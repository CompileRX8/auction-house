define ['angular'],
  (angular) ->
    mod = angular.module('AuctionHouse.services', [])

    mod.service 'userService', ['$http', '$q',
      ($http, $q) ->
        {
          loginUser: (credentials) ->
            $http.post('/login', credentials).then (response) ->
              response.data

          logoutUser: (token) ->
            $http.post('/logout', { token: token }).then (response) ->
              response.data
        }
      ]

    mod.service 'bidderService', ['$http',
      ($http) ->
        {
          addBidder: (name) ->
            $http.post('/bidders', { name: name })

          addPayment: (bidder_id, description, amount) ->
            paymentdata = { description: description, amount: amount }
            $http.post('/payments/' + bidder_id, paymentdata)
        }
    ]

    mod.service 'bidEntryService', ['$http',
      ($http) ->
        {
          addWinningBid: (itemid, bidder_id, amount) ->
            biddata = { bidderId: parseInt(bidder_id), amount: parseFloat(amount) }
            $http.post('/items/' + itemid + "/bid", biddata)

          deleteWinningBid: (id) ->
            $http.post('/items/' + id + '/deletebid', {})
        }
    ]

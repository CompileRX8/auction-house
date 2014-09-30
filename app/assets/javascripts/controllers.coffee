define ['angular'],
  (angular) ->
    mod = angular.module 'AuctionHouse.controllers', ['AuctionHouse.services']

    mod.controller 'HomeCtrl', ['$scope', 'dataService',
      ($scope, dataService) ->
        console.log "Created HomeCtrl"
        $scope.dataService = dataService

        dataService.pushBidders()
        dataService.pushItems()
    ]

    mod.controller 'BiddersCtrl', ['$scope', 'bidderService', 'dataService',
      ($scope, bidderService, dataService) ->
        console.log "Created BiddersCtrl"
        $scope.payment = {}
        $scope.bidder_name = ''

        $scope.dataService = dataService
        $scope.bidderService = bidderService

        $scope.addBidder = ->
          bidderService.addBidder($scope.bidder_name)
          $scope.bidder_name = ''

        $scope.addPayment = ->
          bidderService.addPayment($scope.bidderService.activebidder.bidder.id, $scope.payment.description, parseFloat($scope.payment.amount))
          $scope.payment = {}

        dataService.pushBidders()
    ]

    mod.controller 'ItemsCtrl', ['$scope', 'itemService', 'dataService',
      ($scope, itemService, dataService) ->
        console.log "Creates ItemsCtrl"
        $scope.dataService = dataService
        $scope.itemService = itemService

        dataService.pushItems()
    ]

    mod.controller 'BidEntryCtrl', ['$scope', 'bidEntryService', 'dataService',
      ($scope, bidEntryService, dataService) ->
        console.log "Created BidEntryCtrl"
        $scope.dataService = dataService

        $scope.winningbid = {}

        $scope.filtered_items = ->
          itemnum = $scope.winningbid.item_num
          dataService.itemsdata.filter( (itemdata) -> itemdata.item.itemNumber is itemnum )

        $scope.item_num_change = ->
          filteredItems = $scope.filtered_items()

          $scope.winningbid.item_desc = if(filteredItems.length is 1)
            filteredItems[0].item.description
          else
            '...'

        $scope.bidder_id_change = ->
          bidderid = parseInt($scope.winningbid.bidder_id)
          filteredBidders = dataService.biddersdata.filter( (bidderdata) -> bidderdata.bidder.id is bidderid )

          $scope.winningbid.bidder_name = if(filteredBidders.length is 1)
            filteredBidders[0].bidder.name
          else
            '...'

        $scope.addWinningBid = ->
          filteredItems = $scope.filtered_items()

          if(filteredItems.length is 1)
            itemid = filteredItems[0].item.id
            bidEntryService.addWinningBid(itemid, $scope.winningbid.bidder_id, $scope.winningbid.amount)
            $scope.winningbid = {}

        $scope.deleteWinningBid = (id) ->
          bidEntryService.deleteWinningBid(id)

        $scope.setActiveBid = (id) ->
          $scope.winningbid.id = id

        $scope.clearActiveBid = ->
          $scope.winningbid.id = 0

        dataService.pushBidders()
        dataService.pushItems()
    ]

    mod.controller 'HeaderCtrl', ['$scope', 'userService',
      ($scope, userService) ->
        console.log "Created HeaderCtrl"
        $scope.user = {}
        $scope.credentials = {}

        $scope.login = ->
          userService.loginUser($scope.credentials).then (user) ->
            $scope.user = user

        $scope.logout = ->
          userService.logoutUser($scope.user.token).then (data) ->
            $scope.user = undefined
            $scope.credentials = {}
    ]

    mod.controller 'FooterCtrl', ->

    mod.controller 'ReconCtrl', ['$scope', 'dataService',
      ($scope, dataService) ->
        $scope.dataService = dataService
    ]

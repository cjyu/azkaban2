
$.namespace('azkaban');

var loginView;
azkaban.LoginView= Backbone.View.extend({
  events : {
    "click #loginSubmit": "handleLogin",
    'keypress input': 'handleKeyPress'
  },
  initialize : function(settings) {
	 $('#errorMsg').hide();
  },
  handleLogin : function(evt) {
	 console.log("Logging in.");
	 var username = $("#username").val();
	 var password = $("#password").val();
	 
     $.ajax({
     	async: "false",
     	url: contextURL,
     	dataType: "json",
     	type: "POST",
     	data: {action:"login", username:username, password:password},
     	success: function(data) {
     		if (data.error) {
     			$('#errorMsg').text(data.error);
     			$('#errorMsg').slideDown('fast');
     		}
     		else {
     			document.location.reload();
     		}
     	}
     });
  },
  handleKeyPress : function(evt) {
	  if (evt.charCode == 13 || evt.keyCode == 13) {
	  	this.handleLogin();
  	  }
  },
  render: function() {
  }
});

$(function() {
	loginView = new azkaban.LoginView({el:$('#loginForm')});
});

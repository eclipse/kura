var ExtensionRegistry=function(){this.extensions={};this.consumers=[]};ExtensionRegistry.prototype.registerExtension=function(b){this.extensions[b.id]=b;for(var a=0;a<this.consumers.length;a++){(this.consumers[a])(b)}};ExtensionRegistry.prototype.unregisterExtension=function(a){delete this.extensions[a.id]};ExtensionRegistry.prototype.addExtensionConsumer=function(a){this.consumers.push(a);for(var b in this.extensions){a(this.extensions[b])}};ExtensionRegistry.prototype.getExtensions=function(){var a=[];for(var b in this.extensions){a.push(this.extensions[b])}return a};if(!window.top.extensionRegistry){window.top.extensionRegistry=new ExtensionRegistry()};
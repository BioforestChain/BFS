using System;
using Foundation;
using ObjCRuntime;
using UIKit;
using WebKit;

namespace BrowserFramework
{
	// @interface BrowserManager : NSObject
	[BaseType (typeof(NSObject))]
	interface BrowserManager
	{
		// @property (nonatomic, strong) UIView * _Nullable swiftView;
		[NullAllowed, Export ("swiftView", ArgumentSemantic.Strong)]
		UIView SwiftView { get; set; }

		// @property (nonatomic) NSInteger webViewCount;
		[Export ("webViewCount")]
		nint WebViewCount { get; set; }

		// -(void)fetchHomeDataWithParam:(NSArray<NSDictionary<NSString *,NSString *> *> * _Nonnull)param webViewList:(NSArray<WKWebView *> * _Nonnull)webViewList;
		[Export ("fetchHomeDataWithParam:webViewList:")]
		void FetchHomeDataWithParam (NSDictionary<NSString, NSString>[] param, WKWebView[] webViewList);

		// -(void)addNewWkWebViewWithWebView:(WKWebView * _Nonnull)webView;
		[Export ("addNewWkWebViewWithWebView:")]
		void AddNewWkWebViewWithWebView (WKWebView webView);

		// -(void)showWebViewListDataWithList:(NSArray<WKWebView *> * _Nonnull)list;
		[Export ("showWebViewListDataWithList:")]
		void ShowWebViewListDataWithList (WKWebView[] list);

		// -(void)clickAppActionWithCallback:(void (^ _Nonnull)(NSString * _Nonnull))callback;
		[Export ("clickAppActionWithCallback:")]
		void ClickAppActionWithCallback (Action<NSString> callback);

		// -(void)clickAddNewHomePageActionWithCallback:(void (^ _Nonnull)(void))callback;
		[Export ("clickAddNewHomePageActionWithCallback:")]
		void ClickAddNewHomePageActionWithCallback (Action callback);
	}

	// @interface DownloadAppManager : NSObject
	[BaseType (typeof(NSObject))]
	[DisableDefaultCtor]
	interface DownloadAppManager
	{
		// @property (nonatomic, strong) UIView * _Nullable downloadView;
		[NullAllowed, Export ("downloadView", ArgumentSemantic.Strong)]
		UIView DownloadView { get; set; }

		// -(instancetype _Nonnull)initWithData:(NSDictionary<NSString *,id> * _Nonnull)data isLoaded:(BOOL)isLoaded __attribute__((objc_designated_initializer));
		[Export ("initWithData:isLoaded:")]
		[DesignatedInitializer]
		NativeHandle Constructor (NSDictionary<NSString, NSObject> data, bool isLoaded);

		// -(void)onListenProgressWithProgress:(float)progress;
		[Export ("onListenProgressWithProgress:")]
		void OnListenProgressWithProgress (float progress);

		// -(void)clickDownloadActionWithCallback:(void (^ _Nonnull)(NSDictionary<NSString *,NSString *> * _Nonnull))callback;
		[Export ("clickDownloadActionWithCallback:")]
		void ClickDownloadActionWithCallback (Action<NSDictionary<NSString, NSString>> callback);

		// -(void)downloadComplete;
		[Export ("downloadComplete")]
		void DownloadComplete ();

		// -(void)downloadFail;
		[Export ("downloadFail")]
		void DownloadFail ();
	}
}

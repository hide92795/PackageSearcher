PackageSearcher
======================
このライブラリはパッケージ名からそのパッケージに属するクラスを簡単に取得するためのものです。
このライブラリは[BeanShell](http://www.beanshell.org/)ライブラリを改変しています。
BeanShellのライセンスについては付属のlicense.txtをお読みください。
 
使い方
------
    public static void main() {
	PackageSearcher.init();
       HashSet<String> classes = PackageSearcher.search("hoge.hoge");
    }

関連情報
--------
[BeanShell](http://www.beanshell.org/)
 
ライセンス
----------
このライブラリはGNU Lesser General Public License(LGPL) バージョン3にライセンスされています。
ライセンス条項は付属のテキストファイルか以下のリンクより御覧ください。

[LGPL](http://www.gnu.org/licenses/lgpl.html)